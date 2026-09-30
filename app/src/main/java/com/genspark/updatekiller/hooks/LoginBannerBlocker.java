package com.genspark.updatekiller.hooks;

import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;
import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.UpdateEventLogger;
import com.genspark.updatekiller.UxLog;
import com.genspark.updatekiller.hooks.LoginBannerBlocker;
import de.robv.android.xposed.XC_MethodHook;
import java.lang.reflect.Method;
import java.util.Locale;
import org.json.JSONObject;

/* loaded from: classes.dex */
public final class LoginBannerBlocker {
    private static final String[] LOGIN_BANNER_TEXTS = {"سجّل الدخول", "سجل الدخول", "تم تسجيل", "اشترك", "اشترك أو سجّل الدخول", "اشترك أو سجل الدخول", "تسجيل الدخول", "سجل الدخول", "login to unlock", "sign in to unlock", "subscribe or sign in", "subscribe or login", "login required", "subscribe to continue", "اشترك للمتابعة", "سجل للمتابعة",
            // v2.0.3: subscription upsell banner
            "اشترك الآن", "اشترك في", "الاشتراك", "الباقة", "الخطة", "برو", "بريميوم", "الترقية",
            "subscribe now", "go pro", "get premium", "upgrade to", "premium required", "pro required",
            "unlock premium", "unlock pro",
            // v2.0.3: credit-exhaustion dialogs
            "الرصيد", "رصيدك", "استنفذت", "استنفدت", "نفد رصيدك", "نفذ رصيدك", "انتهى رصيدك",
            "نقاطك", "لا تملك رصيد", "رصيد غير كاف", "اشحن رصيدك",
            "out of credits", "insufficient credits", "credit exhausted", "credits exhausted",
            "no credits", "not enough credits", "credit balance", "out of quota",
            "quota exceeded", "quota exhausted", "daily limit", "limit reached"};
    private static final String[] LOGIN_FLAG_KEYS = {"is_logged_in", "isLoggedIn", "logged_in", "loggedIn", "require_login", "requireLogin", "show_login_banner", "showLoginBanner", "is_subscribed", "isSubscribed", "is_subscribed", "subscriber", "subscribed", "user_subscribed", "user_logged_in", "hasAccount", "isAuthenticated", "is_authenticated", "isPro", "is_pro", "isPlus", "is_plus", "isPremium", "is_premium", "OnBoardingFinished", "onboarding_finished", "onboarding_completed", "showOnboarding", "show_onboarding"};
    private static volatile int depthPasses = 0;

    private LoginBannerBlocker() {
    }

    public static int install(ClassLoader classLoader) {
        if (!Config.get().blockLoginBanner) {
            UxLog.i("LoginBannerBlocker: disabled");
            return 0;
        }
        int hookViewTree = hookViewTree(classLoader) + 0 + hookAlertDialog(classLoader) + hookFlutter(classLoader) + hookPrefs(classLoader);
        UxLog.i("LoginBannerBlocker: " + hookViewTree + " hook(s) [4 layers] — banner removal active");
        UpdateEventLogger.log("session", "LoginBannerBlocker v2: " + hookViewTree + " hook(s)");
        return hookViewTree;
    }

    private static int hookViewTree(ClassLoader classLoader) {
        Class<?> findClass = Reflect.findClass("android.view.ViewGroup", classLoader);
        int hookAllNamed = findClass != null ? 0 + Reflect.hookAllNamed(findClass, "addView", new AnonymousClass1()) : 0;
        Class<?> findClass2 = Reflect.findClass("android.view.WindowManager", classLoader);
        return findClass2 != null ? hookAllNamed + Reflect.hookAllNamed(findClass2, "addView", new AnonymousClass2()) : hookAllNamed;
    }

    /* renamed from: com.genspark.updatekiller.hooks.LoginBannerBlocker$1, reason: invalid class name */
    static class AnonymousClass1 extends XC_MethodHook {
        AnonymousClass1() {
        }

        @Override // de.robv.android.xposed.XC_MethodHook
        protected void afterHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("LoginBannerBlocker.addView", new Guard.Action() { // from class: com.genspark.updatekiller.hooks.LoginBannerBlocker$1$$ExternalSyntheticLambda0
                @Override // com.genspark.updatekiller.Guard.Action
                public final void run() throws Throwable {
                    LoginBannerBlocker.AnonymousClass1.lambda$afterHookedMethod$0(methodHookParam);
                }
            });
        }

        static /* synthetic */ void lambda$afterHookedMethod$0(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
            if (methodHookParam.thisObject instanceof ViewGroup) {
                LoginBannerBlocker.depthPasses++;
                for (int i = 0; i < methodHookParam.args.length; i++) {
                    if (methodHookParam.args[i] instanceof View) {
                        LoginBannerBlocker.checkAndHide((View) methodHookParam.args[i]);
                    }
                }
            }
        }
    }

    /* renamed from: com.genspark.updatekiller.hooks.LoginBannerBlocker$2, reason: invalid class name */
    static class AnonymousClass2 extends XC_MethodHook {
        AnonymousClass2() {
        }

        @Override // de.robv.android.xposed.XC_MethodHook
        protected void afterHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("LoginBannerBlocker.Window.addView", new Guard.Action() { // from class: com.genspark.updatekiller.hooks.LoginBannerBlocker$2$$ExternalSyntheticLambda0
                @Override // com.genspark.updatekiller.Guard.Action
                public final void run() throws Throwable {
                    LoginBannerBlocker.AnonymousClass2.lambda$afterHookedMethod$0(methodHookParam);
                }
            });
        }

        static /* synthetic */ void lambda$afterHookedMethod$0(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
            for (Object obj : methodHookParam.args) {
                if (obj instanceof View) {
                    LoginBannerBlocker.checkAndHide((View) obj);
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void checkAndHide(View view) {
        if (view == null) {
            return;
        }
        try {
            if (view.getBackground() instanceof ColorDrawable) {
                int color = ((ColorDrawable) view.getBackground()).getColor();
                if (isBannerRed(color)) {
                    hideDeep(view, "background-color #" + Integer.toHexString(color).toUpperCase(Locale.ROOT));
                    return;
                }
            }
        } catch (Throwable unused) {
        }
        String readTextDeep = readTextDeep(view);
        if (readTextDeep != null) {
            for (String str : LOGIN_BANNER_TEXTS) {
                if (readTextDeep.contains(str)) {
                    hideDeep(view, "text contains '" + str + "'");
                    return;
                }
            }
        }
        if (view.getWidth() <= 0 || !(view.getParent() instanceof View)) {
            return;
        }
        try {
            int width = ((ViewGroup) view.getParent()).getWidth();
            if (width > 0) {
                int width2 = (view.getWidth() * 100) / width;
            }
        } catch (Throwable unused2) {
        }
    }

    private static boolean isBannerRed(int i) {
        try {
            int red = Color.red(i);
            int green = Color.green(i);
            int blue = Color.blue(i);
            if (Color.alpha(i) < 32) {
                return false;
            }
            return (red >= 130 && green <= 60 && blue <= 60) || (red >= 150 && green <= 80 && blue <= 80);
        } catch (Throwable unused) {
            return false;
        }
    }

    private static String readTextDeep(View view) {
        CharSequence text;
        if ((view instanceof TextView) && (text = ((TextView) view).getText()) != null && text.length() > 0) {
            return text.toString();
        }
        int i = 0;
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            if (viewGroup.getChildCount() <= 0) {
                return null;
            }
            StringBuilder sb = new StringBuilder();
            while (i < viewGroup.getChildCount()) {
                String readTextDeep = readTextDeep(viewGroup.getChildAt(i));
                if (readTextDeep != null) {
                    sb.append(readTextDeep).append(' ');
                }
                i++;
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
            return null;
        }
        CharSequence contentDescription = view.getContentDescription();
        if (contentDescription != null && contentDescription.length() > 0) {
            return contentDescription.toString();
        }
        String lowerCase = view.getClass().getName().toLowerCase(Locale.ROOT);
        String[] strArr = LOGIN_BANNER_TEXTS;
        int length = strArr.length;
        while (i < length) {
            String str = strArr[i];
            if (lowerCase.contains("banner") || lowerCase.contains("paywall") || lowerCase.contains("nudge")) {
                return lowerCase;
            }
            i++;
        }
        return null;
    }

    private static void hideDeep(View view, String str) {
        try {
            view.setVisibility(8);
            if (view instanceof ViewGroup) {
                ViewGroup viewGroup = (ViewGroup) view;
                for (int i = 0; i < viewGroup.getChildCount(); i++) {
                    View childAt = viewGroup.getChildAt(i);
                    if (childAt != null) {
                        childAt.setVisibility(8);
                    }
                }
            }
            try {
                ViewParent parent = view.getParent();
                if (parent instanceof ViewGroup) {
                    ((ViewGroup) parent).removeView(view);
                }
            } catch (Throwable unused) {
            }
            view.addOnLayoutChangeListener(new View.OnLayoutChangeListener() { // from class: com.genspark.updatekiller.hooks.LoginBannerBlocker.3
                @Override // android.view.View.OnLayoutChangeListener
                public void onLayoutChange(View view2, int i2, int i3, int i4, int i5, int i6, int i7, int i8, int i9) {
                    if (view2.getVisibility() != 8) {
                        view2.setVisibility(8);
                    }
                }
            });
            UxLog.i("LoginBannerBlocker: REMOVED banner — " + str);
            UpdateEventLogger.log("login", "removed 'سجّل الدخول' — " + str);
        } catch (Throwable th) {
            Guard.record("LoginBannerBlocker.hideDeep", th);
        }
    }

    private static int hookAlertDialog(ClassLoader classLoader) {
        Class<?> findClass = Reflect.findClass("android.app.AlertDialog", classLoader);
        if (findClass == null) {
            return 0;
        }
        return Reflect.hookAllNamed(findClass, "show", new AnonymousClass4()) + 0;
    }

    /* renamed from: com.genspark.updatekiller.hooks.LoginBannerBlocker$4, reason: invalid class name */
    static class AnonymousClass4 extends XC_MethodHook {
        AnonymousClass4() {
        }

        @Override // de.robv.android.xposed.XC_MethodHook
        protected void afterHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("LoginBannerBlocker.dialogShow", new Guard.Action() { // from class: com.genspark.updatekiller.hooks.LoginBannerBlocker$4$$ExternalSyntheticLambda0
                @Override // com.genspark.updatekiller.Guard.Action
                public final void run() throws Throwable {
                    LoginBannerBlocker.AnonymousClass4.lambda$afterHookedMethod$0(methodHookParam);
                }
            });
        }

        static /* synthetic */ void lambda$afterHookedMethod$0(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
            if (methodHookParam.thisObject instanceof AlertDialog) {
                AlertDialog alertDialog = (AlertDialog) methodHookParam.thisObject;
                String dump = LoginBannerBlocker.dump(alertDialog);
                if (dump == null) {
                    return;
                }
                String lowerCase = dump.toLowerCase(Locale.ROOT);
                for (String str : LoginBannerBlocker.LOGIN_BANNER_TEXTS) {
                    if (dump.contains(str) || lowerCase.contains(str.toLowerCase(Locale.ROOT))) {
                        try {
                            alertDialog.dismiss();
                        } catch (Throwable unused) {
                        }
                        UpdateEventLogger.log("login", "dismissed dialog with '" + str + "'");
                        UxLog.i("LoginBannerBlocker: dismissed AlertDialog containing '" + str + "'");
                        return;
                    }
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String dump(AlertDialog alertDialog) {
        try {
            StringBuilder sb = new StringBuilder();
            Object invoke = Reflect.invoke(alertDialog, null, "getMessage", new Object[0]);
            if (invoke != null) {
                sb.append(invoke.toString());
            }
            Object invoke2 = Reflect.invoke(alertDialog, null, "getTitle", new Object[0]);
            if (invoke2 != null) {
                sb.append('|').append(invoke2.toString());
            }
            return sb.toString();
        } catch (Throwable unused) {
            return null;
        }
    }

    private static int hookFlutter(ClassLoader classLoader) {
        Class<?> ch = Reflect.findClass("io.flutter.plugin.common.MethodChannel", classLoader);
        if (ch == null) {
            return 0;
        }
        try {
            int n = Reflect.hookAllNamed(ch, "invokeMethod", new AnonymousClass5());
            if (n == 0) {
                Guard.record("LoginBannerBlocker.invoke-reflect",
                        new NoSuchMethodException("invokeMethod not found"));
            }
            return Math.max(n, 0);
        } catch (Throwable th) {
            Guard.record("LoginBannerBlocker.invokeResult-reflect", th);
            return 0;
        }
    }

    /* renamed from: com.genspark.updatekiller.hooks.LoginBannerBlocker$5, reason: invalid class name */
    static class AnonymousClass5 extends XC_MethodHook {
        AnonymousClass5() {
        }

        @Override // de.robv.android.xposed.XC_MethodHook
        protected void beforeHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("LoginBannerBlocker.invokeMethod", new Guard.Action() { // from class: com.genspark.updatekiller.hooks.LoginBannerBlocker$5$$ExternalSyntheticLambda0
                @Override // com.genspark.updatekiller.Guard.Action
                public final void run() throws Throwable {
                    LoginBannerBlocker.AnonymousClass5.lambda$beforeHookedMethod$0(methodHookParam);
                }
            });
        }

        static /* synthetic */ void lambda$beforeHookedMethod$0(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
            String valueOf;
            if (methodHookParam.args == null || methodHookParam.args.length < 1 || (valueOf = String.valueOf(methodHookParam.args[0])) == null) {
                return;
            }
            String lowerCase = valueOf.toLowerCase(Locale.ROOT);
            if (lowerCase.contains("login") || lowerCase.contains("subscribe") || lowerCase.contains("signin") || lowerCase.contains("auth") || lowerCase.contains("banner") || lowerCase.contains("onboard") || lowerCase.contains("paywall") || lowerCase.contains("plan") || lowerCase.contains("showbanner") || lowerCase.contains("show")) {
                if (lowerCase.contains("is") || lowerCase.contains("get") || lowerCase.contains("check") || lowerCase.contains("status") || lowerCase.contains("plan")) {
                    JSONObject jSONObject = new JSONObject();
                    try {
                        jSONObject.put("subscribed", true);
                        jSONObject.put("isSubscribed", true);
                        jSONObject.put("isLoggedIn", true);
                        jSONObject.put("status", "active");
                        jSONObject.put("plan", "pro");
                    } catch (Throwable unused) {
                    }
                    methodHookParam.setResult(jSONObject);
                    UpdateEventLogger.log("login", "force-TRUE: " + valueOf);
                    return;
                }
                methodHookParam.setResult(null);
                UpdateEventLogger.log("login", "blocked: " + valueOf);
            }
        }
    }

    /* renamed from: com.genspark.updatekiller.hooks.LoginBannerBlocker$6, reason: invalid class name */
    static class AnonymousClass6 extends XC_MethodHook {
        AnonymousClass6() {
        }

        @Override // de.robv.android.xposed.XC_MethodHook
        protected void beforeHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("LoginBannerBlocker.invokeWithResult", new Guard.Action() { // from class: com.genspark.updatekiller.hooks.LoginBannerBlocker$6$$ExternalSyntheticLambda0
                @Override // com.genspark.updatekiller.Guard.Action
                public final void run() throws Throwable {
                    LoginBannerBlocker.AnonymousClass6.lambda$beforeHookedMethod$0(methodHookParam);
                }
            });
        }

        static /* synthetic */ void lambda$beforeHookedMethod$0(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
            String valueOf;
            if (methodHookParam.args == null || methodHookParam.args.length < 1 || (valueOf = String.valueOf(methodHookParam.args[0])) == null) {
                return;
            }
            String lowerCase = valueOf.toLowerCase(Locale.ROOT);
            if (lowerCase.contains("login") || lowerCase.contains("subscribe") || lowerCase.contains("paywall") || lowerCase.contains("plan") || lowerCase.contains("banner")) {
                methodHookParam.setResult(null);
                UpdateEventLogger.log("login", "blocked+result: " + valueOf);
            }
        }
    }

    /* JADX WARN: Removed duplicated region for block: B:14:0x0041 A[Catch: all -> 0x004c, TRY_LEAVE, TryCatch #1 {all -> 0x004c, blocks: (B:12:0x002f, B:14:0x0041), top: B:11:0x002f }] */
    /* JADX WARN: Removed duplicated region for block: B:18:? A[RETURN, SYNTHETIC] */
    /*
        Code decompiled incorrectly, please refer to instructions dump.
        To view partially-correct add '--show-bad-code' argument
    */
    private static int hookPrefs(ClassLoader classLoader) {
        Class<?> editor = Reflect.findClass("android.app.SharedPreferencesImpl$EditorImpl", classLoader);
        if (editor == null) {
            return 0;
        }
        int hooks = 0;
        java.lang.reflect.Method putBoolean = null;
        try {
            putBoolean = editor.getDeclaredMethod("putBoolean", String.class, Boolean.TYPE);
        } catch (Throwable t) {
            Guard.record("LoginBannerBlocker.putBoolean-reflect", t);
        }
        if (putBoolean != null && Reflect.hook(putBoolean, new AnonymousClass7())) {
            hooks++;
        }
        java.lang.reflect.Method putString = null;
        try {
            putString = editor.getDeclaredMethod("putString", String.class, String.class);
        } catch (Throwable t) {
            Guard.record("LoginBannerBlocker.putString-reflect", t);
        }
        if (putString != null && Reflect.hook(putString, new AnonymousClass8())) {
            hooks++;
        }
        return hooks;
    }

    /* renamed from: com.genspark.updatekiller.hooks.LoginBannerBlocker$7, reason: invalid class name */
    static class AnonymousClass7 extends XC_MethodHook {
        AnonymousClass7() {
        }

        @Override // de.robv.android.xposed.XC_MethodHook
        protected void beforeHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("LoginBannerBlocker.putBoolean", new Guard.Action() { // from class: com.genspark.updatekiller.hooks.LoginBannerBlocker$7$$ExternalSyntheticLambda0
                @Override // com.genspark.updatekiller.Guard.Action
                public final void run() throws Throwable {
                    LoginBannerBlocker.AnonymousClass7.lambda$beforeHookedMethod$0(methodHookParam);
                }
            });
        }

        static /* synthetic */ void lambda$beforeHookedMethod$0(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
            String valueOf = (methodHookParam.args == null || methodHookParam.args.length <= 1) ? "" : String.valueOf(methodHookParam.args[0]);
            if (valueOf == null) {
                return;
            }
            String lowerCase = valueOf.toLowerCase(Locale.ROOT);
            for (String str : LoginBannerBlocker.LOGIN_FLAG_KEYS) {
                if (lowerCase.contains(str.toLowerCase(Locale.ROOT))) {
                    methodHookParam.args[1] = Boolean.TRUE;
                    UpdateEventLogger.log("prefs.login", "forced '" + valueOf + "'=true");
                    return;
                }
            }
        }
    }

    /* renamed from: com.genspark.updatekiller.hooks.LoginBannerBlocker$8, reason: invalid class name */
    static class AnonymousClass8 extends XC_MethodHook {
        AnonymousClass8() {
        }

        @Override // de.robv.android.xposed.XC_MethodHook
        protected void beforeHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("LoginBannerBlocker.putString", new Guard.Action() { // from class: com.genspark.updatekiller.hooks.LoginBannerBlocker$8$$ExternalSyntheticLambda0
                @Override // com.genspark.updatekiller.Guard.Action
                public final void run() throws Throwable {
                    LoginBannerBlocker.AnonymousClass8.lambda$beforeHookedMethod$0(methodHookParam);
                }
            });
        }

        static /* synthetic */ void lambda$beforeHookedMethod$0(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
            String valueOf = (methodHookParam.args == null || methodHookParam.args.length <= 1) ? "" : String.valueOf(methodHookParam.args[0]);
            if (valueOf == null) {
                return;
            }
            String lowerCase = valueOf.toLowerCase(Locale.ROOT);
            for (String str : LoginBannerBlocker.LOGIN_FLAG_KEYS) {
                if (lowerCase.contains(str.toLowerCase(Locale.ROOT))) {
                    methodHookParam.args[1] = "true";
                    UpdateEventLogger.log("prefs.login", "forced '" + valueOf + "'='true'");
                    return;
                }
            }
        }
    }
}
