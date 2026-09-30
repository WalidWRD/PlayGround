package com.genspark.updatekiller.hooks;

import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.UxLog;

import de.robv.android.xposed.XC_MethodHook;

/**
 * يبطل Play Core In-App Update (إن استخدمته نسخة أخرى من التطبيق).
 * إن لم تكن المكتبة موجودة لا يحدث أي شيء إطلاقًا.
 */
public final class PlayCoreHook {

    private PlayCoreHook() { }

    public static int install(ClassLoader cl) {
        if (!Config.get().playCore) { UxLog.i("PlayCoreHook: disabled by config"); return 0; }
        int n = 0;

        Class<?> info = Reflect.findClass("com.google.android.play.core.appupdate.AppUpdateInfo", cl);
        if (info != null) {
            n += Reflect.hookAllNamed(info, "isUpdateTypeAllowed", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Guard.run("PlayCoreHook.isUpdateTypeAllowed", () -> {
                        p.setResult(Boolean.FALSE);
                        UxLog.i("PlayCoreHook: isUpdateTypeAllowed → false");
                    });
                }
            });
            n += Reflect.hookAllNamed(info, "updateAvailability", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Guard.run("PlayCoreHook.updateAvailability", () -> {
                        if (p.getResult() instanceof Integer && ((Integer) p.getResult()).intValue() != 1) {
                            p.setResult(Integer.valueOf(1)); // UPDATE_NOT_AVAILABLE
                        }
                    });
                }
            });
        }
        Class<?> mgr = Reflect.findClass("com.google.android.play.core.appupdate.AppUpdateManager", cl);
        if (mgr != null) {
            for (java.lang.reflect.Method m : Reflect.methodsNamed(mgr, "startUpdateFlow")) {
                Reflect.hook(m, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam p) {
                        Guard.run("PlayCoreHook.startUpdateFlow", () -> UxLog.i("PlayCoreHook: startUpdateFlow observed"));
                    }
                });
                n++;
            }
        }
        UxLog.i("PlayCoreHook: " + n + " hook(s)" + (info == null ? " (Play Core absent — nothing to do)" : ""));
        return n;
    }
}
