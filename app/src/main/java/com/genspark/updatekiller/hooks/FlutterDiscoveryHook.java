package com.genspark.updatekiller.hooks;

import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.UpdateEventLogger;
import com.genspark.updatekiller.UxLog;

/**
 * v2.0.2: removed bogus reflection (Class.forName(..FlutterEngineCache..)
 * + invoke(null, cls, "constructor") always returned null and logged a
 * misleading "channel cached"). Now purely observational: report which
 * known channels exist, never throws.
 */
public final class FlutterDiscoveryHook {
    private FlutterDiscoveryHook() {
    }

    public static int install(ClassLoader classLoader) {
        if (Reflect.findClass("io.flutter.plugin.common.MethodChannel", classLoader) != null) {
            UpdateEventLogger.log("flutter", "MethodChannel class loaded in target");
            UxLog.i("FlutterDiscovery: MethodChannel LOADED in target");
            logKnownChannels();
            return 1;
        }
        UxLog.w("FlutterDiscovery: MethodChannel not loaded yet (Flutter not initialized)");
        return 0;
    }

    private static void logKnownChannels() {
        String[] channels = {"plugins.flutter.io/google_sign_in", "plugins.flutter.io/firebase_auth",
                "plugins.flutter.io/shared_preferences", "ai.mainfunc.genspark/native",
                "ai.mainfunc.genspark/auth", "ai.mainfunc.genspark/subscription",
                "ai.mainfunc.genspark/paywall", "ai.mainfunc.genspark/banner",
                "ai.mainfunc.genspark/feature_gate", "ai.mainfunc.genspark/onboarding",
                "ai.mainfunc.genspark.vip"};
        for (String ch : channels) {
            UpdateEventLogger.log("flutter", "known channel: " + ch);
            UxLog.d("FlutterDiscovery: watching " + ch);
        }
    }
}
