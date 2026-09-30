package com.genspark.updatekiller;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * معلومات البيئة والهدف (جديد 2.0.0) — كل شيء بالانعكاس: لا استيراد لأنواع الهدف،
 * ولا افتراض وجود ActivityThread أو أي حقل، وأي فشل يُبتلع بأمان.
 */
public final class EnvInfo {

    /** مسارات الفئات الفعلية (sourceDir + splitSourceDirs) — تُستخدم لفهرسة DEX. */
    private static volatile String[] codePathsCache;
    private static volatile long codePathsAt = 0L;

    private EnvInfo() { }

    public static Object appInfo() {
        Object app = Reflect.currentApplication();
        if (app != null) {
            Object ai = Reflect.invokeNoArg(app, "getApplicationInfo");
            if (ai != null) return ai;
        }
        Object at = activityThread();
        if (at != null) {
            Object app2 = Reflect.invokeNoArg(at, "getApplication");
            if (app2 != null) {
                Object ai = Reflect.invokeNoArg(app2, "getApplicationInfo");
                if (ai != null) return ai;
            }
            Object ai = Reflect.invokeNoArg(at, "getApplicationInfo");
            if (ai != null) return ai;
        }
        return null;
    }

    public static Object activityThread() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            java.lang.reflect.Method m = at.getDeclaredMethod("currentActivityThread");
            m.setAccessible(true);
            return m.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    public static String sourceDir() {
        Object ai = appInfo();
        Object v = Reflect.getField(ai, "sourceDir");
        return (v != null) ? String.valueOf(v) : "";
    }

    public static File sourceDirFile() {
        String s = sourceDir();
        if (s == null || s.length() == 0) return null;
        File f = new File(s);
        return f.exists() ? f : null;
    }

    public static File[] splitFiles() {
        List<File> out = new ArrayList<File>();
        Object ai = appInfo();
        Object splits = Reflect.getField(ai, "splitSourceDirs");
        if (splits instanceof String[]) {
            for (String s : (String[]) splits) {
                if (s == null) continue;
                File f = new File(s);
                if (f.exists()) out.add(f);
            }
        } else if (splits instanceof Object[]) {
            for (Object o : (Object[]) splits) {
                if (o == null) continue;
                File f = new File(String.valueOf(o));
                if (f.exists()) out.add(f);
            }
        }
        return out.toArray(new File[0]);
    }

    public static String nativeLibraryDir() {
        Object ai = appInfo();
        Object v = Reflect.getField(ai, "nativeLibraryDir");
        return (v != null) ? String.valueOf(v) : "";
    }

    public static String[] abis() {
        Object ai = appInfo();
        Object v = Reflect.getField(ai, "primaryCpuAbi");
        Object v2 = Reflect.getField(ai, "secondaryCpuAbi");
        List<String> out = new ArrayList<String>();
        if (v != null) out.add(String.valueOf(v));
        if (v2 != null) out.add(String.valueOf(v2));
        if (out.isEmpty()) out.add("unknown");
        return out.toArray(new String[0]);
    }

    public static int apiLevel() {
        try {
            Class<?> v = Class.forName("android.os.Build$VERSION");
            java.lang.reflect.Field f = v.getField("SDK_INT");
            Object o = f.get(null);
            if (o instanceof Integer) return (Integer) o;
        } catch (Throwable ignored) { }
        return -1;
    }

    public static String packageName() {
        Object ai = appInfo();
        Object v = Reflect.getField(ai, "packageName");
        String s = (v != null) ? String.valueOf(v) : "";
        return s;
    }

    /** versionCode الحقيقي (غير مُزوَّر) للهدف — يُقرأ من ApplicationInfo مباشرة. */
    public static long realVersionCode() {
        Object ai = appInfo();
        Object lv = Reflect.getField(ai, "longVersionCode");
        if (lv instanceof Long) return (Long) lv;
        Object v = Reflect.getField(ai, "versionCode");
        if (v instanceof Integer) return (Integer) v;
        return -1L;
    }

    public static String realVersionName() {
        Object ai = appInfo();
        Object v = Reflect.getField(ai, "versionName");
        return (v != null) ? String.valueOf(v) : "";
    }

    public static String[] codePaths() {
        String[] c = codePathsCache;
        if (c != null && System.currentTimeMillis() - codePathsAt < 600000L) return c;
        List<String> out = new ArrayList<String>();
        String sd = sourceDir();
        if (sd != null && sd.length() > 0) out.add(sd);
        for (File f : splitFiles()) out.add(f.getAbsolutePath());
        c = out.toArray(new String[0]);
        codePathsCache = c;
        codePathsAt = System.currentTimeMillis();
        return c;
    }

    /**
     * محاولة رصد إطار الدمج (Patch framework) بعلامات معروفة.
     * ملاحظة صريحة: هذا رصد «أفضل جهد» بالعلامات — قد يُعيد unknown في بعض البنى.
     */
    public static String patchFramework(ClassLoader cl) {
        String[][] markers = {
            {"LSPatch", "org.lsposed.lspatch.loader.LSPApplication"},
            {"LSPatch", "org.lsposed.lspatch.loader.LSPAppComponentFactoryStub"},
            {"LSPatch", "org.lsposed.lspatch.share.Constants"},
            {"NPatch",  "org.lsposed.npatch.loader.NPApplication"},
            {"NPatch",  "org.lsposed.npatch.share.Constants"},
            {"NPatch",  "org.lsposed.npatch.NPatchApplication"},
            {"HKP",     "com.hkp.patch.HKPApplication"},
            {"HKP",     "com.hkp.patch.loader.HKPApplication"},
            {"HKP",     "hkp.patch.PatchApplication"},
            {"LSPosed", "org.lsposed.lspd.core.Main"},
            {"Xposed",  "de.robv.android.xposed.XposedBridge"}
        };
        StringBuilder sb = new StringBuilder();
        for (String[] m : markers) {
            if (Reflect.hasClass(m[1], cl)) {
                if (sb.length() > 0) sb.append('+');
                sb.append(m[0]);
            }
        }
        if (sb.length() == 0) {
            Object ai = appInfo();
            if (ai != null) {
                Object md = Reflect.getField(ai, "metaData");
                if (md != null) {
                    try {
                        java.lang.reflect.Method get = md.getClass().getMethod("getString", String.class);
                        get.setAccessible(true);
                        for (String k : new String[]{"lspatch", "npatch", "hkp", "xposedmodule", "lsposed"}) {
                            Object v = null;
                            try { v = get.invoke(md, k); } catch (Throwable ignored) { }
                            if (v != null && sb.length() == 0) sb.append("meta:").append(k);
                        }
                    } catch (Throwable ignored) { }
                }
            }
        }
        if (sb.length() == 0) sb.append("unknown");
        String xv = xposedBridgeVersion(cl);
        if (xv != null && xv.length() > 0) sb.append(" (bridgeApi=").append(xv).append(')');
        return sb.toString();
    }

    private static String xposedBridgeVersion(ClassLoader cl) {
        Class<?> xb = Reflect.findClass("de.robv.android.xposed.XposedBridge", cl);
        if (xb == null) return "";
        try {
            java.lang.reflect.Method m = xb.getMethod("getXposedVersion");
            m.setAccessible(true);
            Object v = m.invoke(null);
            return (v != null) ? String.valueOf(v) : "";
        } catch (Throwable t) {
            return "";
        }
    }

    public static String describe(ClassLoader cl) {
        return "api=" + apiLevel()
             + ", abi=" + join(abis())
             + ", framework=" + patchFramework(cl)
             + ", target=" + packageName() + "@" + realVersionName() + "(" + realVersionCode() + ")"
             + ", dexIndex=" + Discovery.names() + " (" + Discovery.scanSource() + ")";
    }

    private static String join(String[] a) {
        StringBuilder sb = new StringBuilder();
        for (String s : a) {
            if (s == null) continue;
            if (sb.length() > 0) sb.append('|');
            sb.append(s);
        }
        return sb.toString();
    }
}
