package com.genspark.updatekiller.hooks;

import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.SubscriptionBridge;
import com.genspark.updatekiller.UpdateEventLogger;
import com.genspark.updatekiller.UxLog;
import de.robv.android.xposed.XC_MethodHook;
import java.util.Locale;
import org.json.JSONObject;

/**
 * v2.0.1 fixes:
 * - hook ALL MethodChannel.invokeMethod overloads (2-arg AND 3-arg with
 *   MethodChannel.Result). v2.0.0 only hooked (String, Object), so every
 *   Flutter call that passes a Result callback (google_sign_in,
 *   shared_preferences, firebase_auth) bypassed the spoof entirely.
 * - keep the OR filter (any keyword matches), rewritten cleanly so it
 *   compiles (v2.0.0 dex logic was correct but jadx-duplicated Guard.run).
 */
public final class SubscriptionEnforcer {
    private SubscriptionEnforcer() {
    }

    public static int install(ClassLoader classLoader) {
        if (!Config.get().forceSubscribed) {
            UxLog.i("SubscriptionEnforcer: disabled (forceSubscribed=false) — passive mode");
            return 0;
        }
        int hookFlutterSubscriptions = hookFlutterSubscriptions(classLoader) + 0 + hookSharedPrefsForSubscription(classLoader);
        UxLog.i("SubscriptionEnforcer: " + hookFlutterSubscriptions + " hook(s) — subscription enforced");
        UpdateEventLogger.log("subscription", "enforced subscription state (forceSubscribed=true)");
        return hookFlutterSubscriptions;
    }

    private static int hookFlutterSubscriptions(ClassLoader classLoader) {
        Class<?> findClass = Reflect.findClass("io.flutter.plugin.common.MethodChannel", classLoader);
        if (findClass == null) {
            return 0;
        }
        try {
            // FIX: hook every overload named invokeMethod, not just (String, Object).
            int n = Reflect.hookAllNamed(findClass, "invokeMethod", new AnonymousClass1());
            if (n == 0) {
                Guard.record("SubscriptionEnforcer.invoke-reflect",
                        new NoSuchMethodException("invokeMethod not found"));
            }
            return n > 0 ? n : 0;
        } catch (Throwable th) {
            Guard.record("SubscriptionEnforcer.invoke-reflect", th);
            return 0;
        }
    }

    static boolean isSubscriptionQuery(String lower) {
        if (lower.contains("is") || lower.contains("get") || lower.contains("check")
                || lower.contains("status") || lower.contains("plan") || lower.contains("sub")) {
            return true;
        }
        if (lower.contains("login") || lower.contains("subscri") || lower.contains("signin")
                || lower.contains("account") || lower.contains("auth") || lower.contains("userinfo")
                || lower.contains("me")) {
            return true;
        }
        // v2.0.3: premium/credit/ultra queries also return lifetime (defense in depth)
        return lower.contains("credit") || lower.contains("balance") || lower.contains("quota")
                || lower.contains("premium") || lower.contains("ultra") || lower.contains("vip")
                || lower.contains("entitle");
    }

    /* renamed from: com.genspark.updatekiller.hooks.SubscriptionEnforcer$1 */
    static class AnonymousClass1 extends XC_MethodHook {
        AnonymousClass1() {
        }

        @Override
        protected void beforeHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            try {
                if (methodHookParam.args == null || methodHookParam.args.length < 1) {
                    return;
                }
                final String valueOf = String.valueOf(methodHookParam.args[0]);
                if (valueOf == null) {
                    return;
                }
                String lowerCase = valueOf.toLowerCase(Locale.ROOT);
                if (!isSubscriptionQuery(lowerCase)) {
                    return;
                }
                Guard.run("SubscriptionEnforcer.flutter", new Guard.Action() {
                    @Override
                    public void run() throws Throwable {
                        AnonymousClass1.lambda$beforeHookedMethod$0(valueOf, methodHookParam);
                    }
                });
            } catch (Throwable th) {
                Guard.record("SubscriptionEnforcer.invoke", th);
            }
        }

        static void lambda$beforeHookedMethod$0(String str, XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
            Object synthesizeActive = SubscriptionBridge.synthesizeActive(str);
            if (synthesizeActive instanceof JSONObject) {
                SubscriptionBridge.coerceSubscribedFields(synthesizeActive);
            }
            methodHookParam.setResult(synthesizeActive);
            UpdateEventLogger.log("auth", "FORCED active subscription for: " + str);
        }
    }

    private static int hookSharedPrefsForSubscription(ClassLoader classLoader) {
        Class<?> findClass = Reflect.findClass("android.app.SharedPreferencesImpl$EditorImpl", classLoader);
        if (findClass == null) {
            return 0;
        }
        try {
            java.lang.reflect.Method declaredMethod = findClass.getDeclaredMethod("putBoolean", String.class, Boolean.TYPE);
            if (declaredMethod == null) {
                return 0;
            }
            Reflect.hook(declaredMethod, new AnonymousClass2());
            return 1;
        } catch (Throwable th) {
            Guard.record("SubscriptionEnforcer.putBoolean-reflect", th);
            return 0;
        }
    }

    /* renamed from: com.genspark.updatekiller.hooks.SubscriptionEnforcer$2 */
    static class AnonymousClass2 extends XC_MethodHook {
        AnonymousClass2() {
        }

        @Override
        protected void beforeHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("SubscriptionEnforcer.prefs", new Guard.Action() {
                @Override
                public void run() throws Throwable {
                    AnonymousClass2.lambda$beforeHookedMethod$0(methodHookParam);
                }
            });
        }

        static void lambda$beforeHookedMethod$0(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
            String valueOf = (methodHookParam.args == null || methodHookParam.args.length <= 1) ? "" : String.valueOf(methodHookParam.args[0]);
            if (valueOf == null) {
                return;
            }
            String lowerCase = valueOf.toLowerCase(Locale.ROOT);
            if (lowerCase.contains("issubscribed") || lowerCase.contains("is_subscribed") || lowerCase.contains("issubscriber") || lowerCase.contains("issub") || lowerCase.contains("isloggedin") || lowerCase.contains("is_logged_in") || lowerCase.contains("ispro") || lowerCase.contains("is_pro") || lowerCase.contains("isplus") || lowerCase.contains("is_plus") || lowerCase.contains("ispremium") || lowerCase.contains("is_premium") || lowerCase.contains("showloginbanner") || lowerCase.contains("show_login_banner") || lowerCase.contains("isauthenticated") || lowerCase.contains("is_authenticated") || lowerCase.contains("hasaccount") || lowerCase.contains("has_account") || lowerCase.contains("onboardingfinished") || lowerCase.contains("onboarding_finished")) {
                methodHookParam.args[1] = Boolean.TRUE;
                UpdateEventLogger.log("sub.prefs", "FORCED '" + valueOf + "'=true");
            }
        }
    }
}
