package com.genspark.updatekiller.hooks;

import android.app.Activity;
import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.UpdateEventLogger;
import com.genspark.updatekiller.UxLog;
import com.genspark.updatekiller.hooks.LoginBypassHook;
import de.robv.android.xposed.XC_MethodHook;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/* loaded from: classes.dex */
public final class LoginBypassHook {
    private static final List<String> LOGIN_ACTIVITY_HINTS = Arrays.asList("login", "signup", "signin", "sign_in", "register", "auth", "onboard", "welcome", "intro", "splash");

    private LoginBypassHook() {
    }

    public static int install(ClassLoader classLoader) {
        if (!Config.get().bypassLogin) {
            UxLog.i("LoginBypassHook: disabled");
            return 0;
        }
        Class<?> findClass = Reflect.findClass("android.app.Activity", classLoader);
        int hookAllNamed = findClass != null ? Reflect.hookAllNamed(findClass, "onCreate", new AnonymousClass1()) + 0 : 0;
        Class<?> findClass2 = Reflect.findClass("android.app.SharedPreferencesImpl$EditorImpl", classLoader);
        if (findClass2 != null) {
            try {
                Method declaredMethod = findClass2.getDeclaredMethod("putBoolean", String.class, Boolean.TYPE);
                if (declaredMethod != null) {
                    Reflect.hook(declaredMethod, new AnonymousClass2());
                    hookAllNamed++;
                }
            } catch (Throwable th) {
                Guard.record("LoginBypassHook.putBoolean-reflect", th);
            }
        }
        UxLog.i("LoginBypassHook: " + hookAllNamed + " hook(s) — bypass gate active");
        return hookAllNamed;
    }

    /* renamed from: com.genspark.updatekiller.hooks.LoginBypassHook$1, reason: invalid class name */
    static class AnonymousClass1 extends XC_MethodHook {
        AnonymousClass1() {
        }

        @Override // de.robv.android.xposed.XC_MethodHook
        protected void beforeHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("LoginBypassHook.onCreate", new Guard.Action() { // from class: com.genspark.updatekiller.hooks.LoginBypassHook$1$$ExternalSyntheticLambda0
                @Override // com.genspark.updatekiller.Guard.Action
                public final void run() throws Throwable {
                    LoginBypassHook.AnonymousClass1.lambda$beforeHookedMethod$0(methodHookParam);
                }
            });
        }

        static /* synthetic */ void lambda$beforeHookedMethod$0(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
            if (methodHookParam.thisObject instanceof Activity) {
                String lowerCase = methodHookParam.thisObject.getClass().getName().toLowerCase(Locale.ROOT);
                Iterator it = LoginBypassHook.LOGIN_ACTIVITY_HINTS.iterator();
                while (it.hasNext()) {
                    if (lowerCase.contains((String) it.next())) {
                        Activity activity = (Activity) methodHookParam.thisObject;
                        UpdateEventLogger.log("login", "short-circuiting " + lowerCase);
                        UxLog.i("LoginBypassHook: finishing login activity " + lowerCase);
                        activity.finish();
                        return;
                    }
                }
            }
        }
    }

    /* renamed from: com.genspark.updatekiller.hooks.LoginBypassHook$2, reason: invalid class name */
    static class AnonymousClass2 extends XC_MethodHook {
        AnonymousClass2() {
        }

        @Override // de.robv.android.xposed.XC_MethodHook
        protected void beforeHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("LoginBypassHook.putBoolean", new Guard.Action() { // from class: com.genspark.updatekiller.hooks.LoginBypassHook$2$$ExternalSyntheticLambda0
                @Override // com.genspark.updatekiller.Guard.Action
                public final void run() throws Throwable {
                    LoginBypassHook.AnonymousClass2.lambda$beforeHookedMethod$0(methodHookParam);
                }
            });
        }

        static /* synthetic */ void lambda$beforeHookedMethod$0(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
            String valueOf = (methodHookParam.args == null || methodHookParam.args.length <= 0) ? "" : String.valueOf(methodHookParam.args[0]);
            if (valueOf == null) {
                return;
            }
            String lowerCase = valueOf.toLowerCase(Locale.ROOT);
            if (lowerCase.contains("logged") || lowerCase.contains("onboarding") || lowerCase.contains("finished") || lowerCase.contains("auth")) {
                methodHookParam.args[1] = Boolean.TRUE;
                UpdateEventLogger.log("login", "forced pref '" + valueOf + "'=true");
            }
        }
    }
}
