package com.genspark.updatekiller.hooks;

import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.SubscriptionBridge;
import com.genspark.updatekiller.UpdateEventLogger;
import com.genspark.updatekiller.UxLog;
import de.robv.android.xposed.XC_MethodHook;
import java.util.Locale;

/** v2.0.2: hook ALL invokeMethod overloads (was 2-arg only, missed Result callbacks). */
public final class GoogleSignInSpoofHook {
    private GoogleSignInSpoofHook() {
    }

    public static int install(ClassLoader classLoader) {
        if (!Config.get().spoofGoogleSignIn && !Config.get().forceSubscribed) {
            UxLog.i("GoogleSignInSpoofHook: disabled");
            return 0;
        }
        Class<?> findClass = Reflect.findClass("io.flutter.plugin.common.MethodChannel", classLoader);
        if (findClass == null) {
            return 0;
        }
        try {
            int n = Reflect.hookAllNamed(findClass, "invokeMethod", new AnonymousClass1());
            UxLog.i("GoogleSignInSpoofHook: " + n + " hook(s)");
            return n;
        } catch (Throwable th) {
            Guard.record("GoogleSignInSpoofHook.invoke-reflect", th);
            return 0;
        }
    }

    /* renamed from: com.genspark.updatekiller.hooks.GoogleSignInSpoofHook$1 */
    static class AnonymousClass1 extends XC_MethodHook {
        AnonymousClass1() {
        }

        @Override
        protected void beforeHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("GoogleSignInSpoofHook.invoke", new Guard.Action() {
                @Override
                public void run() throws Throwable {
                    AnonymousClass1.lambda$beforeHookedMethod$0(methodHookParam);
                }
            });
        }

        static void lambda$beforeHookedMethod$0(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
            if (methodHookParam.args != null) {
                boolean z = true;
                if (methodHookParam.args.length < 1) {
                    return;
                }
                String valueOf = String.valueOf(methodHookParam.args[0]);
                Object obj = methodHookParam.thisObject;
                if (!(obj == null ? "" : obj.getClass().getName()).toLowerCase(Locale.ROOT).contains("google_sign_in") && !valueOf.toLowerCase(Locale.ROOT).contains("google")) {
                    z = false;
                }
                if (z) {
                    String lowerCase = valueOf.toLowerCase(Locale.ROOT);
                    if (lowerCase.contains("signout") || lowerCase.contains("disconnect")) {
                        methodHookParam.setResult(null);
                    } else {
                        methodHookParam.setResult(SubscriptionBridge.synthesizeActive(valueOf));
                        UpdateEventLogger.log("auth", "spoofed Google Sign-In: " + valueOf);
                    }
                }
            }
        }
    }
}
