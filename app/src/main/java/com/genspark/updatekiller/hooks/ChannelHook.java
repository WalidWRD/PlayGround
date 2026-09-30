package com.genspark.updatekiller.hooks;

import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.UpdateEventLogger;
import com.genspark.updatekiller.UxLog;
import de.robv.android.xposed.XC_MethodHook;
import java.util.Locale;

/**
 * v2.0.1 fixes:
 * - setMethodCallHandler lookup now uses the TARGET classLoader
 *   (v2.0.0 used boot Class.forName, which always throws inside the target
 *   process, so the 2nd hook never installed).
 * - invokeMethod: hook ALL overloads (2-arg + 3-arg with Result).
 */
public final class ChannelHook {
    private ChannelHook() {
    }

    public static int install(ClassLoader classLoader) {
        Class<?> findClass = Reflect.findClass("io.flutter.plugin.common.MethodChannel", classLoader);
        if (findClass == null) {
            UxLog.i("ChannelHook: MethodChannel not found");
            return 0;
        }
        int hooks = 0;
        try {
            hooks += Reflect.hookAllNamed(findClass, "invokeMethod", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    ChannelHook.Guard_run(methodHookParam);
                }
            });
        } catch (Throwable t) {
            Guard.record("ChannelHook.invoke-reflect", t);
        }
        try {
            // FIX: resolve via target loader, not boot loader.
            Class<?> handler = Reflect.findClass("io.flutter.plugin.common.MethodChannel$MethodCallHandler", classLoader);
            if (handler != null) {
                for (java.lang.reflect.Method m : Reflect.methodsNamed(findClass, "setMethodCallHandler")) {
                    if (m.getParameterTypes().length == 1
                            && m.getParameterTypes()[0].isAssignableFrom(handler)) {
                        if (Reflect.hook(m, new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                                UxLog.i("ChannelHook: MethodCallHandler attached");
                            }
                        })) {
                            hooks++;
                        }
                    }
                }
            }
        } catch (Throwable t) {
            Guard.record("ChannelHook.handler-reflect", t);
        }
        UxLog.i("ChannelHook: " + hooks + " hook(s)");
        return hooks;
    }

    static void Guard_run(XC_MethodHook.MethodHookParam methodHookParam) {
        String valueOf;
        try {
            if (methodHookParam.args == null || methodHookParam.args.length < 1 || (valueOf = String.valueOf(methodHookParam.args[0])) == null) {
                return;
            }
            String lowerCase = valueOf.toLowerCase(Locale.ROOT);
            if (lowerCase.contains("update") || lowerCase.contains("forceupgrade") || lowerCase.contains("store") || lowerCase.contains("login") || lowerCase.contains("signin") || lowerCase.contains("subscribe") || lowerCase.contains("auth") || lowerCase.contains("banner") || lowerCase.contains("onboard")) {
                UpdateEventLogger.log("channel", "flutter call: " + valueOf);
            }
        } catch (Throwable th) {
            final Throwable t = th;
            Guard.run("ChannelHook.invokeMethod", new Guard.Action() {
                @Override
                public void run() throws Throwable {
                    ChannelHook.lambda$Guard_run$0(t);
                }
            });
        }
    }

    static void lambda$Guard_run$0(Throwable th) throws Throwable {
        throw th;
    }
}
