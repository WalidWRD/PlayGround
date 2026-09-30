package com.genspark.updatekiller.hooks;

import android.content.pm.PackageInfo;
import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.UpdateEventLogger;
import com.genspark.updatekiller.UxLog;
import de.robv.android.xposed.XC_MethodHook;

/**
 * v2.0.1 fixes:
 * - REMOVED dead hooks for PackageInfo.setVersionCode/setVersionName:
 *   those methods DO NOT EXIST (versionCode/versionName are public fields),
 *   so v2.0.0 always logged 0 hooks for that half and confused users.
 * - Hook EVERY getPackageInfo overload (incl. API 33+ PackageInfoFlags),
 *   not just the first 2-arg method found in iterator order.
 * - Also spoof getLongVersionCode (API 28+) / longVersionCode field.
 */
public final class VersionSpoofHook {
    private VersionSpoofHook() {
    }

    public static int install(ClassLoader classLoader) {
        if (!Config.get().spoofVersion) {
            UxLog.i("VersionSpoofHook: disabled");
            return 0;
        }
        int hooks = 0;
        Class<?> pm = Reflect.findClass("android.content.pm.PackageManager", classLoader);
        if (pm != null) {
            for (java.lang.reflect.Method method : Reflect.methodsNamed(pm, "getPackageInfo")) {
                if (Reflect.hook(method, new AnonymousClass3())) {
                    hooks++;
                }
            }
            // API 28+: PackageInfo.getLongVersionCode() — virtual, hook by name.
            Class<?> pi = Reflect.findClass("android.content.pm.PackageInfo", classLoader);
            if (pi != null) {
                hooks += Reflect.hookAllNamed(pi, "getLongVersionCode", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                        Guard.run("VersionSpoof.getLongVersionCode", new Guard.Action() {
                            @Override
                            public void run() throws Throwable {
                                param.setResult((long) Config.get().spoofVersionCode);
                            }
                        });
                    }
                });
            }
        }
        UxLog.i("VersionSpoofHook: " + hooks + " hook(s)");
        UpdateEventLogger.log("session", "spoof=" + Config.get().spoofVersionCode + "/" + Config.get().spoofVersionName);
        return hooks;
    }

    /* renamed from: com.genspark.updatekiller.hooks.VersionSpoofHook$3 */
    static class AnonymousClass3 extends XC_MethodHook {
        AnonymousClass3() {
        }

        @Override
        protected void afterHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("VersionSpoof.getPackageInfo", new Guard.Action() {
                @Override
                public void run() throws Throwable {
                    AnonymousClass3.lambda$afterHookedMethod$0(methodHookParam);
                }
            });
        }

        static void lambda$afterHookedMethod$0(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
            Object result = methodHookParam.getResult();
            if (result instanceof PackageInfo) {
                PackageInfo packageInfo = (PackageInfo) result;
                packageInfo.versionCode = Config.get().spoofVersionCode;
                packageInfo.versionName = Config.get().spoofVersionName;
                try {
                    packageInfo.getClass().getField("longVersionCode").setLong(packageInfo, (long) Config.get().spoofVersionCode);
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
