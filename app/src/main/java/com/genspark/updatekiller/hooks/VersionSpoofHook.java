package com.genspark.updatekiller.hooks;

import com.genspark.updatekiller.BuildInfo;
import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.UxLog;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;

/**
 * الآلية الحاسمة (v2.0.0): يجعل التطبيق يقرأ إصداره أعلى من أي حدٍّ أدنى يفرضه الخادم،
 * فيصبح الشرط (localVersionCode &lt; minAppVersionCode) غير مُحقّق ولا يظهر الحوار —
 * سواء من جهة Dart/Flutter أو من جهة Java.
 *
 * v2.0.0: لم تبقَ أسماء الفئات ثابتة كليًا؛ هذا الخطّاف يربط ما يستطيع ربطه من الفئات
 * المعروفة، و{@link PackageManagerDiscoveryHook} يُكمل الباقي عبر فهرس DEX
 * (كل تطبيق PackageManager يُكتشف ديناميكيًا) — مع تشارك نفس منطق التزوير هنا.
 */
public final class VersionSpoofHook {

    private VersionSpoofHook() { }

    public static int install(ClassLoader cl) {
        final Config cfg = Config.get();
        if (!cfg.spoofVersion) { UxLog.i("VersionSpoof: disabled by config"); return 0; }
        int n = 0;

        Class<?> apm = Reflect.findClass("android.app.ApplicationPackageManager", cl);
        if (apm != null) {
            n += Reflect.hookAllNamed(apm, "getPackageInfo", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Guard.run("VersionSpoof.getPackageInfo", () -> boost(p));
                }
            });
        } else {
            UxLog.w("VersionSpoof: ApplicationPackageManager not found via loader");
        }

        // احتياط: بعض البيئات الافتراضية تُعيد صنفًا مختلفًا — نربط الكلاس المجرد أيضًا.
        Class<?> pmc = Reflect.findClass("android.content.pm.PackageManager", cl);
        if (pmc != null && apm != null && !pmc.getName().equals(apm.getName())) {
            n += Reflect.hookAllNamed(pmc, "getPackageInfo", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Guard.run("VersionSpoof.pm.getPackageInfo", () -> boost(p));
                }
            });
        }

        // مسار الـApplicationInfo (تقرأه بعض إصدارات Flutter وقناة package_info أيضًا)
        Class<?> act = Reflect.findClass("android.app.ActivityThread", cl);
        if (act != null) {
            n += Reflect.hookAllNamed(act, "getApplicationInfo", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Guard.run("VersionSpoof.getApplicationInfo", () -> boostApp(p));
                }
            });
        }

        Class<?> pi = Reflect.findClass("android.content.pm.PackageInfo", cl);
        if (pi != null) {
            n += Reflect.hookAllNamed(pi, "getLongVersionCode", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Guard.run("VersionSpoof.getLongVersionCode", () -> {
                        Object self = p.thisObject;
                        String pkg = String.valueOf(Reflect.getField(self, "packageName"));
                        if (isSelf(pkg)) p.setResult(Long.valueOf(Config.get().spoofVersionCode));
                    });
                }
            });
        }
        UxLog.i("VersionSpoof: " + n + " hook(s) — versionCode→" + cfg.spoofVersionCode
                + ", versionName→" + cfg.spoofVersionName);
        return n;
    }

    private static void boost(MethodHookParam p) {
        Object r = p.getResult();
        if (r == null) {
            // بعض الإصدارات تُعيد null عبر الواجهة المجردة → نمنع التسريب بلا تغيير سلوك المرسل
            return;
        }
        boostPackageInfo(r);
    }

    private static void boostApp(MethodHookParam p) {
        Object r = p.getResult();
        if (r == null) return;
        boostApplicationInfo(r);
    }

    /** يزوّر حقول PackageInfo للهدف فقط (يُستدعى من هذا الخطّاف ومن الاكتشاف الديناميكي). */
    public static void boostPackageInfo(Object obj) {
        if (!(obj instanceof android.content.pm.PackageInfo)) return;
        try {
            android.content.pm.PackageInfo info = (android.content.pm.PackageInfo) obj;
            if (!isSelf(info.packageName)) return;
            Config cfg = Config.get();
            try { info.versionCode = cfg.spoofVersionCode; } catch (Throwable ignored) { }
            try { info.versionName = cfg.spoofVersionName; } catch (Throwable ignored) { }
            Reflect.setField(info, "longVersionCode", Long.valueOf(cfg.spoofVersionCode));
            Reflect.setField(info, "versionCode", Integer.valueOf(cfg.spoofVersionCode));
            Reflect.setField(info, "versionName", cfg.spoofVersionName);
            UxLog.d("VersionSpoof: " + info.packageName + " → versionCode=" + cfg.spoofVersionCode
                    + ", versionName=" + cfg.spoofVersionName);
        } catch (Throwable t) {
            Guard.record("VersionSpoof.boostPackageInfo", t);
        }
    }

    /** يزوّر حقول ApplicationInfo للهدف فقط. */
    public static void boostApplicationInfo(Object obj) {
        if (!(obj instanceof android.content.pm.ApplicationInfo)) return;
        try {
            android.content.pm.ApplicationInfo ai = (android.content.pm.ApplicationInfo) obj;
            if (!isSelf(ai.packageName)) return;
            Config cfg = Config.get();
            Reflect.setField(ai, "longVersionCode", Long.valueOf(cfg.spoofVersionCode));
            Reflect.setField(ai, "versionCode", Integer.valueOf(cfg.spoofVersionCode));
            Reflect.setField(ai, "versionName", cfg.spoofVersionName);
            UxLog.d("VersionSpoof: ApplicationInfo " + ai.packageName + " → " + cfg.spoofVersionCode);
        } catch (Throwable t) {
            Guard.record("VersionSpoof.boostApplicationInfo", t);
        }
    }

    public static boolean isSelf(String pkg) {
        if (pkg == null) return false;
        for (String t : BuildInfo.PACKAGE_NAMES) if (t.equals(pkg)) return true;
        Config c = Config.get();
        for (String t : c.packages) if (t != null && t.equals(pkg)) return true;
        return pkg.startsWith("ai.mainfunc.genspark");
    }
}
