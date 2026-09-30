package com.genspark.updatekiller.hooks;

import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.UpdateEventLogger;
import com.genspark.updatekiller.UxLog;
import de.robv.android.xposed.XC_MethodHook;
import java.lang.reflect.Method;

/**
 * v2.0.1: clean rewrite (v2.0.0 dex logic preserved, source was
 * uncompilable due to uninitialized-register pattern).
 */
public final class BlutterTrace {
    private BlutterTrace() {
    }

    public static int install(ClassLoader classLoader) {
        if (!Config.get().blutterTrace) {
            UxLog.i("BlutterTrace: disabled");
            return 0;
        }
        int hooks = 0;
        try {
            Class<?> runtime = Reflect.findClass("java.lang.Runtime", classLoader);
            if (runtime == null) {
                return 0;
            }
            Method load = null;
            try {
                load = runtime.getDeclaredMethod("load", String.class);
            } catch (Throwable t) {
                Guard.record("BlutterTrace.load-reflect", t);
            }
            if (load != null && Reflect.hook(load, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    String valueOf;
                    if (methodHookParam.args == null || methodHookParam.args.length <= 0 || (valueOf = String.valueOf(methodHookParam.args[0])) == null || !valueOf.contains("libapp.so")) {
                        return;
                    }
                    UpdateEventLogger.log("native", "libapp.so about to load: " + valueOf);
                    UxLog.i("BlutterTrace: libapp.so loading: " + valueOf);
                }
            })) {
                hooks++;
            }
            for (Method method : runtime.getDeclaredMethods()) {
                if ("loadLibrary".equals(method.getName())
                        && method.getParameterTypes().length == 1
                        && method.getParameterTypes()[0] == String.class) {
                    if (Reflect.hook(method, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                            String valueOf;
                            if (methodHookParam.args == null || methodHookParam.args.length <= 0 || (valueOf = String.valueOf(methodHookParam.args[0])) == null || !valueOf.contains("app")) {
                                return;
                            }
                            UpdateEventLogger.log("native", "loadLibrary: " + valueOf);
                            UxLog.i("BlutterTrace: loadLibrary(" + valueOf + ")");
                        }
                    })) {
                        hooks++;
                    }
                }
            }
        } catch (Throwable t) {
            Guard.record("BlutterTrace.install", t);
        }
        UxLog.i("BlutterTrace: " + hooks + " hook(s)");
        return hooks;
    }
}
