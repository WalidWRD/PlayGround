package com.genspark.updatekiller.hooks;

import android.content.pm.PackageManager;

import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Discovery;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.UxLog;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;

/**
 * جديد 2.0.0 — «اكتشاف PackageManager» الديناميكي.
 *
 * لماذا: في بعض البِنى (مساحات افتراضية، مُحاكيات، بعض إصدارات Android أو بيئات
 * الدمج) لا يكون صنف تنفيذ PackageManager هو ApplicationPackageManager المعروف،
 * أو يُغلَّف/DexClassLoader مخصّص. هنا نستخرج **كل** صنف في التطبيق يُنفّذ/يورث
 * android.content.pm.PackageManager من فهرس DEX، ونربط كل overloads القراءة:
 *   getPackageInfo(...) / getPackageInfoFlags(...) / getApplicationInfo(...) /
 *   getInstalledPackages(...) / getInstalledApplications(...)
 * ثم نُزوّر حقول الإصدار للهدف فقط — فتصبح الآلية محصّنة ضد تغيّر الإصدارات.
 *
 * Idempotent: كل صنف يُربط مرة واحدة (Set)، وإعادة التشغيل في المرورات اللاحقة
 * تلتقط الأصناف التي ظهرت بعد الإقلاع فقط.
 */
public final class PackageManagerDiscoveryHook {

    private static final Set<String> HOOKED = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static volatile ClassLoader loader;
    private static volatile int total = 0;

    private PackageManagerDiscoveryHook() { }

    public static int install(ClassLoader cl) {
        if (!Config.get().pmDiscovery) return 0;
        if (cl != null) loader = cl;
        final ClassLoader c = (loader != null) ? loader : cl;
        if (c == null) return 0;

        int before = total;
        try {
            List<Class<?>> impls = new ArrayList<Class<?>>();

            // 1) الأصناف المعروفة أولًا (سريعة ومضمونة)
            for (String cn : new String[]{
                    "android.app.ApplicationPackageManager",
                    "android.app.ContextImpl",
                    "android.content.pm.PackageManager"}) {
                Class<?> k = Reflect.findClass(cn, c);
                if (k != null && PackageManager.class.isAssignableFrom(k)) impls.add(k);
            }

            // 2) ثم كل ما يُكتشف من فهرس DEX (بحث بالاسم — يعمل مع التشويش الجزئي)
            List<Class<?>> found = Discovery.classesMatching(c, new Discovery.ClassFilter() {
                @Override public boolean accept(String name) {
                    if (name == null) return false;
                    return name.toLowerCase(java.util.Locale.US).indexOf("packagemanager") >= 0;
                }
            }, 80);
            impls.addAll(found);

            for (Class<?> k : impls) {
                if (k == null) continue;
                if (!HOOKED.add("pm:" + k.getName())) continue;
                int n = hookAll(k);
                total += n;
                if (n > 0) UxLog.i("PMDiscovery: hooked " + k.getName() + " (" + n + ")");
            }

            // 3) ربط رنّان ثابت لأنواع PackageInfo/ApplicationInfo المزروعة
            Class<?> pi = Reflect.findClass("android.content.pm.PackageInfo", c);
            if (pi != null && HOOKED.add("pi:getLongVersionCode")) {
                int n = Reflect.hookAllNamed(pi, "getLongVersionCode", new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        Guard.run("PMDiscovery.PackageInfo.getLongVersionCode", () -> {
                            String pkg = String.valueOf(Reflect.getField(p.thisObject, "packageName"));
                            if (VersionSpoofHook.isSelf(pkg)) {
                                p.setResult(Long.valueOf(Config.get().spoofVersionCode));
                            }
                        });
                    }
                });
                total += n;
            }
        } catch (Throwable t) {
            Guard.record("PMDiscovery.install", t);
        }

        int added = total - before;
        if (added > 0) UxLog.i("PMDiscovery: +" + added + " hook(s) (total " + total + ")");
        return total;
    }

    private static int hookAll(final Class<?> k) {
        int n = 0;
        n += hookNamed(k, "getPackageInfo");
        n += hookNamed(k, "getPackageInfoFlags");
        n += hookNamed(k, "getInstalledPackages");
        n += hookNamed(k, "getInstalledApplications");
        n += hookNamed(k, "getApplicationInfo");
        return n;
    }

    private static int hookNamed(Class<?> k, String name) {
        int n = 0;
        for (Method m : Reflect.methodsNamed(k, name)) {
            if (Reflect.hook(m, new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Guard.run("PMDiscovery." + name, () -> {
                        Object r = p.getResult();
                        if (r == null) return;
                        if (r instanceof android.content.pm.PackageInfo) {
                            VersionSpoofHook.boostPackageInfo(r);
                        } else if (r instanceof android.content.pm.ApplicationInfo) {
                            VersionSpoofHook.boostApplicationInfo(r);
                        } else if (r instanceof java.util.List) {
                            java.util.List<?> list = (java.util.List<?>) r;
                            for (Object o : list) {
                                if (o instanceof android.content.pm.PackageInfo) {
                                    VersionSpoofHook.boostPackageInfo(o);
                                } else if (o instanceof android.content.pm.ApplicationInfo) {
                                    VersionSpoofHook.boostApplicationInfo(o);
                                }
                            }
                        }
                    });
                }
            })) n++;
        }
        return n;
    }
}
