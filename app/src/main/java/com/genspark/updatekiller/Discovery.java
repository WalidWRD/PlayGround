package com.genspark.updatekiller;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import de.robv.android.xposed.XC_MethodHook;

/**
 * محرّك الاستكشاف الديناميكي (جديد 2.0.0).
 *
 * الهدف: لا أسماء فئات ثابتة إطلاقًا. يُبنى فهرس أسماء الفئات من داخل APK الهدف
 * نفسه (بمسح تدفّقي لملفات classes*.dex من sourceDir + splitSourceDirs)، ثم
 * تُحلّ الأسماء داخل lpparam.classLoader وتُطابق **بُنْيَوِيًّا** (اسم/توقيع) —
 * لذلك يعمل على الإصدارات الأعلى والأقل، وعلى التطبيقات المضغوطة/المحمية/المشوّشة.
 *
 * إن كان DEX مشفّرًا (حماية 360/梆梆/إيجل) يفشل المسح → يُفعَّل «وضع الرصد الكسول»:
 * نُربط ClassLoader.loadClass/findClass وننتظر تحميل الفئات ثم نطابقها لحظة وصولها
 * (هذا هو المسار الذي يجعل الموديول يصمد أمام الحمايات).
 */
public final class Discovery {

    public interface ClassFilter { boolean accept(String className); }
    public interface ClassObserver { void onLoaded(Class<?> c); }
    public interface ReadyCallback { void onReady(int names, boolean dexScanned); }

    private static final Set<String> NAMES = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final Set<String> SEEN_LOADED = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static final List<ClassObserver> OBSERVERS = Collections.synchronizedList(new ArrayList<ClassObserver>());
    private static final List<ReadyCallback> READY = Collections.synchronizedList(new ArrayList<ReadyCallback>());

    private static final String[] LOADER_INTEREST = {
        "update", "upgrade", "version", "flutter", "packageinfo", "packagemanager",
        "store", "review", "gate", "mainfunc", "genspark"
    };

    private static final String[] FRAMEWORK_PREFIX = {
        "java.", "javax.", "sun.", "jdk.", "android.", "androidx.", "dalvik.", "kotlin.",
        "kotlinx.", "org.json.", "org.w3c.", "org.xml.", "org.apache.", "libcore.",
        "com.android.", "j$.", "org.intellij."
    };

    private static volatile boolean started = false;
    private static volatile boolean dexScanned = false;
    private static volatile boolean loaderObserved = false;
    private static volatile String scanSource = "none";
    private static volatile int maxNames = 250000;
    private static ClassLoader appLoader;

    private static final ExecutorService EXEC = Executors.newSingleThreadExecutor(new ThreadFactory() {
        @Override public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "GensparkDiscovery");
            t.setDaemon(true);
            return t;
        }
    });

    private Discovery() { }

    /** يبدأ المسح مرة واحدة. آمن تمامًا: أي فشل يُسجَّل ولا يُرمى. */
    public static synchronized void start(ClassLoader cl) {
        appLoader = cl;
        if (started) return;
        started = true;
        maxNames = Math.max(1000, Config.get().scanMaxNames);
        observeLoader(cl);
        EXEC.execute(new Runnable() {
            @Override public void run() {
                int n = 0;
                try { n = scanApkDex(); }
                catch (Throwable t) { Guard.record("Discovery.scanApkDex", t); }
                if (n <= 0) {
                    try { n = scanViaDexFileApi(); }
                    catch (Throwable t) { Guard.record("Discovery.scanViaDexFileApi", t); }
                }
                dexScanned = n > 0;
                UxLog.i("Discovery: " + NAMES.size() + " class name(s) indexed (source=" + scanSource + ")");
                SelfTest.record("discovery.names", NAMES.size());
                ReadyCallback[] arr;
                synchronized (READY) { arr = READY.toArray(new ReadyCallback[0]); READY.clear(); }
                for (ReadyCallback cb : arr) {
                    try { cb.onReady(NAMES.size(), dexScanned); } catch (Throwable t) { Guard.record("Discovery.ready", t); }
                }
            }
        });
    }

    public static boolean isDexScanned() { return dexScanned; }
    public static String scanSource() { return scanSource; }
    public static int names() { return NAMES.size(); }

    public static void whenReady(ReadyCallback cb) {
        if (cb == null) return;
        if (dexScanned || (started && NAMES.size() > 0)) {
            try { cb.onReady(NAMES.size(), dexScanned); } catch (Throwable t) { Guard.record("Discovery.ready", t); }
            return;
        }
        synchronized (READY) { READY.add(cb); }
    }

    public static void observe(ClassObserver o) {
        if (o == null) return;
        synchronized (OBSERVERS) { OBSERVERS.add(o); }
    }

    // ───────────────────────── فهرسة أسماء الفئات من DEX ─────────────────────────

    private static int scanApkDex() throws Exception {
        List<File> targets = new ArrayList<File>();
        File src = EnvInfo.sourceDirFile();
        if (src != null) targets.add(src);
        File[] splits = EnvInfo.splitFiles();
        for (File f : splits) if (f != null) targets.add(f);

        int found = 0;
        StringBuilder srcInfo = new StringBuilder();
        for (File f : targets) {
            if (f == null || !f.exists()) continue;
            try {
                if (f.getName().endsWith(".apk") || f.getName().endsWith(".jar") || f.getName().endsWith(".zip")) {
                    ZipFile zf = new ZipFile(f);
                    try {
                        Enumeration<? extends ZipEntry> en = zf.entries();
                        int dex = 0;
                        while (en.hasMoreElements() && dex < 8) {
                            ZipEntry e = en.nextElement();
                            String nm = e.getName();
                            if (!nm.startsWith("classes") || !nm.endsWith(".dex")) continue;
                            dex++;
                            InputStream in = null;
                            try {
                                in = zf.getInputStream(e);
                                found += scanStream(in);
                            } finally {
                                if (in != null) try { in.close(); } catch (Throwable ignored) { }
                            }
                        }
                        if (dex > 0) srcInfo.append(f.getName()).append("[dex=").append(dex).append("] ");
                    } finally {
                        try { zf.close(); } catch (Throwable ignored) { }
                    }
                } else if (f.getName().endsWith(".dex")) {
                    InputStream in = null;
                    try {
                        in = new FileInputStream(f);
                        int c = scanStream(in);
                        found += c;
                        if (c > 0) srcInfo.append(f.getName()).append(" ");
                    } finally {
                        if (in != null) try { in.close(); } catch (Throwable ignored) { }
                    }
                }
            } catch (Throwable t) {
                Guard.record("Discovery.scanFile(" + f.getName() + ")", t);
            }
        }
        scanSource = (srcInfo.length() > 0) ? srcInfo.toString().trim() : "none(encrypted-or-missing)";
        return found;
    }

    /** مسح تدفّقي: يستخرج كل مُعرّفات الفئات (Laaa/bbb;) من التيار بدون تحميل الملف في الذاكرة. */
    private static int scanStream(InputStream in) throws Exception {
        byte[] buf = new byte[262144];
        StringBuilder cur = new StringBuilder(192);
        int add = 0, n;
        while ((n = in.read(buf)) > 0) {
            for (int i = 0; i < n; i++) {
                int b = buf[i] & 0xFF;
                if (b >= 32 && b < 127) {
                    if (cur.length() < 256) cur.append((char) b);
                } else {
                    if (harvest(cur)) add++;
                    cur.setLength(0);
                }
            }
        }
        if (harvest(cur)) add++;
        return add;
    }

    private static boolean harvest(StringBuilder cur) {
        int len = cur.length();
        if (len < 6 || len > 200) return false;
        if (cur.charAt(0) != 'L' || cur.charAt(len - 1) != ';') return false;
        String raw = cur.toString();
        if (raw.indexOf('/') < 0) return false;
        String name = raw.substring(1, len - 1).replace('/', '.');
        if (isFramework(name)) return false;
        if (NAMES.size() >= maxNames) return false;
        return NAMES.add(name);
    }

    private static int scanViaDexFileApi() {
        int total = 0;
        try {
            Class<?> dexFile = Class.forName("dalvik.system.DexFile");
            String[] paths = EnvInfo.codePaths();
            for (String p : paths) {
                if (p == null) continue;
                try {
                    Object df = null;
                    try {
                        df = dexFile.getConstructor(String.class).newInstance(p);
                    } catch (Throwable t) {
                        try { df = dexFile.getConstructor(File.class).newInstance(new File(p)); } catch (Throwable t2) { continue; }
                    }
                    java.lang.reflect.Method entries = dexFile.getMethod("entries");
                    entries.setAccessible(true);
                    Enumeration<?> en = (Enumeration<?>) entries.invoke(df);
                    while (en != null && en.hasMoreElements()) {
                        Object o = en.nextElement();
                        if (!(o instanceof String)) continue;
                        String name = (String) o;
                        if (name == null || isFramework(name)) continue;
                        if (NAMES.add(name)) total++;
                    }
                    try { dexFile.getMethod("close").invoke(df); } catch (Throwable ignored) { }
                } catch (Throwable t) {
                    Guard.record("Discovery.dexFile(" + p + ")", t);
                }
            }
            if (total > 0) scanSource = (scanSource.equals("none")) ? "DexFile.entries()" : scanSource + " + DexFile.entries()";
        } catch (Throwable t) {
            Guard.record("Discovery.scanViaDexFileApi", t);
        }
        return total;
    }

    public static boolean isFramework(String name) {
        if (name == null) return true;
        for (String p : FRAMEWORK_PREFIX) if (name.startsWith(p)) return true;
        return false;
    }

    /** مفتاح المرور السريع لرصد التحميل الكسول. */
    public static boolean interesting(String name) {
        if (name == null || isFramework(name)) return false;
        String l = name.toLowerCase(Locale.US);
        for (String h : LOADER_INTEREST) if (l.indexOf(h) >= 0) return true;
        return false;
    }

    // ───────────────────────── الاستعلام البنيوي ─────────────────────────

    /** كل الفئات التي يحتوي اسمها (بأي حالة) على أي من اللمحات. */
    public static List<Class<?>> classesByName(ClassLoader cl, String[] hints, int limit) {
        List<Class<?>> out = new ArrayList<Class<?>>();
        for (String n : NAMES) {
            if (out.size() >= limit) break;
            if (hints != null && hints.length > 0) {
                String l = n.toLowerCase(Locale.US);
                boolean ok = false;
                for (String h : hints) if (h != null && l.indexOf(h.toLowerCase(Locale.US)) >= 0) { ok = true; break; }
                if (!ok) continue;
            }
            Class<?> c = Reflect.findClass(n, cl);
            if (c != null) out.add(c);
        }
        return out;
    }

    /** كل الفئات المقبولة حسب مُرشِّح مخصص. */
    public static List<Class<?>> classesMatching(ClassLoader cl, ClassFilter filter, int limit) {
        List<Class<?>> out = new ArrayList<Class<?>>();
        for (String n : NAMES) {
            if (out.size() >= limit) break;
            if (filter != null && !filter.accept(n)) continue;
            Class<?> c = Reflect.findClass(n, cl);
            if (c != null) out.add(c);
        }
        return out;
    }

    /** كل الفئات التي تُنفّذ واجهة محدّدة (مقارنة بالاسم — يعمل مع أي إصدار). */
    public static List<Class<?>> classesImplementing(ClassLoader cl, String ifaceName, String[] nameHints, int limit) {
        List<Class<?>> out = new ArrayList<Class<?>>();
        if (ifaceName == null) return out;
        for (String n : NAMES) {
            if (out.size() >= limit) break;
            if (nameHints != null && nameHints.length > 0) {
                String l = n.toLowerCase(Locale.US);
                boolean ok = false;
                for (String h : nameHints) if (l.indexOf(h.toLowerCase(Locale.US)) >= 0) { ok = true; break; }
                if (!ok) continue;
            }
            Class<?> c = Reflect.findClass(n, cl);
            if (c == null || c.isInterface() || java.lang.reflect.Modifier.isAbstract(c.getModifiers())) continue;
            if (Reflect.implementsInterface(c, ifaceName, cl)) out.add(c);
        }
        return out;
    }

    /** الفئات التي ظهرت فعليًا وقت التشغيل (للتشخيص). */
    public static int loadedCount() { return SEEN_LOADED.size(); }

    // ───────────────────────── رصد تحميل الفئات ─────────────────────────

    public static void observeLoader(ClassLoader cl) {
        if (loaderObserved) return;
        loaderObserved = true;
        XC_MethodHook h = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Guard.run("Discovery.loadClass", new Guard.Action() {
                    @Override public void run() {
                        Object r = p.getResult();
                        if (r instanceof Class) notifyLoaded((Class<?>) r);
                    }
                });
            }
        };
        int n = 0;
        Class<?> clc = Reflect.findClass("java.lang.ClassLoader", cl);
        if (clc != null) n += Reflect.hookAllNamed(clc, "loadClass", h);
        Class<?> bd = Reflect.findClass("dalvik.system.BaseDexClassLoader", cl);
        if (bd != null) n += Reflect.hookAllNamed(bd, "findClass", h);
        UxLog.i("Discovery: lazy loader observer = " + n + " hook(s)");
        SelfTest.record("discovery.lazyObserver", n);
    }

    private static void notifyLoaded(Class<?> c) {
        if (c == null) return;
        String name = c.getName();
        if (!interesting(name)) return;
        if (!SEEN_LOADED.add(name)) return;
        if (name.startsWith("com.genspark.updatekiller")) return;
        UxLog.d("Discovery: relevant class loaded → " + name);
        ClassObserver[] arr;
        synchronized (OBSERVERS) { arr = OBSERVERS.toArray(new ClassObserver[0]); }
        for (ClassObserver o : arr) {
            try { o.onLoaded(c); } catch (Throwable t) { Guard.record("Discovery.notifyLoaded", t); }
        }
        DeferredInstaller.kick("class:" + name);
    }
}
