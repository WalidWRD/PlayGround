package com.genspark.updatekiller.hooks;

import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.UpdateEventLogger;
import com.genspark.updatekiller.UxLog;

import de.robv.android.xposed.XC_MethodHook;

/**
 * تحسين 1.4.0 — مسجّل تحميل المكتبات الأصلية + رقم عملية libapp.so.
 * مفيد لتشخيص اللقطة AOT والربط مع blutter_frida.js (الذي يربط كل دالة بعنوانها).
 *
 * مُعدَّل: في الإصدارات السابقة كان رصد فقط؛ الآن نُسجّل الحدث في ملف log ونعطي
 * توجيهًا لرفع مكتبة libapp.so وقت التشغيل عبر Frida بلصق مسار نسختها من الجهاز.
 */
public final class NativeHook {

    private static final String[] LIB_WATCH = { "libapp.so", "libflutter.so" };

    private NativeHook() { }

    public static int install(ClassLoader cl) {
        if (!Config.get().nativeProbe) { UxLog.i("NativeHook: disabled by config"); return 0; }
        int n = 0;

        Class<?> rl = Reflect.findClass("java.lang.Runtime", cl);
        if (rl != null) {
            n += Reflect.hookAllNamed(rl, "loadLibrary", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Guard.run("NativeHook.loadLibrary", () -> {
                        if (p.args != null && p.args.length >= 1) {
                            String name = String.valueOf(p.args[0]);
                            for (String w : LIB_WATCH) if (name.startsWith(w)) {
                                UpdateEventLogger.log("native", "loaded " + name + " — ready for blutter_frida.js attach");
                                UxLog.i("NativeHook: " + name + " loaded");
                            }
                        }
                    });
                }
            });
        }

        Class<?> sys = Reflect.findClass("android.os.Process", cl);
        if (sys != null) {
            n += Reflect.hookAllNamed(sys, "myPid", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Guard.run("NativeHook.myPid", () -> {
                        Object pid = p.getResult();
                        UpdateEventLogger.log("native", "PID=" + pid);
                    });
                }
            });
        }
        UxLog.i("NativeHook: " + n + " hook(s)");
        return n;
    }
}
