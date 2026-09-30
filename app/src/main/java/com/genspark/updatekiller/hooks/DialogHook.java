package com.genspark.updatekiller.hooks;

import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.UpdateEventLogger;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.UxLog;

import de.robv.android.xposed.XC_MethodHook;

/**
 * مراقبة الحوارات الأصلية (Java). افتراضيًا «مراقبة فقط» بلا أي تغيير سلوك،
 * ويمكن تفعيل forceCancellable من ملف الإعدادات لجعل كل حوار قابلًا للإلغاء.
 */
public final class DialogHook {

    private DialogHook() { }

    public static int install(ClassLoader cl) {
        Class<?> d = Reflect.findClass("android.app.Dialog", cl);
        Class<?> ad = Reflect.findClass("androidx.appcompat.app.AlertDialog", cl);
        int n = 0;

        if (d != null) {
            n += Reflect.hookAllNamed(d, "show", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Guard.run("DialogHook.show", () -> {
                        String cn = p.thisObject.getClass().getName();
                        if (Config.get().verbose) UxLog.d("DialogHook: native dialog shown (" + cn + ")");
                        boolean upd = cn != null && (cn.toLowerCase().contains("update") || cn.toLowerCase().contains("upgrade"));
                        UpdateEventLogger.log("dialog", "native dialog shown: " + cn + (upd ? "  (update-related)" : ""));
                    });
                }
            });
            n += Reflect.hookAllNamed(d, "setCancelable", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    Guard.run("DialogHook.setCancelable", () -> {
                        if (!Config.get().dialogWatch) return;
                        if (p.args != null && p.args.length == 1 && Boolean.FALSE.equals(p.args[0])) {
                            p.args[0] = Boolean.TRUE;
                            UxLog.i("DialogHook: setCancelable(false) → true");
                        }
                    });
                }
            });
        }
        if (ad != null) {
            n += Reflect.hookAllNamed(ad, "setCancelable", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    Guard.run("DialogHook.adSetCancelable", () -> {
                        if (!Config.get().dialogWatch) return;
                        if (p.args != null && p.args.length == 1 && Boolean.FALSE.equals(p.args[0])) p.args[0] = Boolean.TRUE;
                    });
                }
            });
        }
        UxLog.i("DialogHook: " + n + " hook(s) (watch=" + (d != null) + ")");
        return n;
    }
}
