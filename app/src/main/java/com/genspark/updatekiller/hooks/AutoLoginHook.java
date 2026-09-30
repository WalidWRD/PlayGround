package com.genspark.updatekiller.hooks;

import android.os.Handler;
import android.os.Looper;
import android.webkit.WebView;
import android.widget.EditText;
import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.UpdateEventLogger;
import com.genspark.updatekiller.UxLog;
import com.genspark.updatekiller.hooks.AutoLoginHook;
import de.robv.android.xposed.XC_MethodHook;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/* loaded from: classes.dex */
public final class AutoLoginHook {
    private static final List<String> USERNAME_FIELD_HINTS = Arrays.asList("email", "username", "user", "login", "mail");
    private static final List<String> PASSWORD_FIELD_HINTS = Arrays.asList("password", "passwd", "pass", "pwd");

    private AutoLoginHook() {
    }

    public static int install(ClassLoader classLoader) {
        if (!Config.get().autoLogin) {
            UxLog.i("AutoLoginHook: disabled by config");
            return 0;
        }
        if (Config.get().loginEmail == null || Config.get().loginEmail.isEmpty() || Config.get().loginPassword == null || Config.get().loginPassword.isEmpty()) {
            UxLog.w("AutoLoginHook: enabled but loginEmail/loginPassword empty — nothing to fill");
            return 0;
        }
        Class<?> findClass = Reflect.findClass("android.widget.EditText", classLoader);
        int hookAllNamed = findClass != null ? Reflect.hookAllNamed(findClass, "setText", new AnonymousClass1()) + 0 : 0;
        Class<?> findClass2 = Reflect.findClass("android.webkit.WebView", classLoader);
        if (findClass2 != null) {
            hookAllNamed += Reflect.hookAllNamed(findClass2, "loadUrl", new AnonymousClass2());
        }
        Class<?> findClass3 = Reflect.findClass("io.flutter.plugin.common.MethodChannel", classLoader);
        if (findClass3 != null) {
            try {
                Method declaredMethod = findClass3.getDeclaredMethod("invokeMethod", String.class, Object.class);
                if (declaredMethod != null) {
                    Reflect.hook(declaredMethod, new AnonymousClass3());
                    hookAllNamed++;
                }
            } catch (Throwable unused) {
            }
        }
        UxLog.i("AutoLoginHook: " + hookAllNamed + " hook(s)");
        return hookAllNamed;
    }

    /* renamed from: com.genspark.updatekiller.hooks.AutoLoginHook$1, reason: invalid class name */
    static class AnonymousClass1 extends XC_MethodHook {
        AnonymousClass1() {
        }

        @Override // de.robv.android.xposed.XC_MethodHook
        protected void afterHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("AutoLoginHook.setText", new Guard.Action() { // from class: com.genspark.updatekiller.hooks.AutoLoginHook$1$$ExternalSyntheticLambda0
                @Override // com.genspark.updatekiller.Guard.Action
                public final void run() throws Throwable {
                    AutoLoginHook.AnonymousClass1.lambda$afterHookedMethod$0(methodHookParam);
                }
            });
        }

        static /* synthetic */ void lambda$afterHookedMethod$0(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
            String str = "";
            if (methodHookParam.thisObject instanceof EditText) {
                EditText editText = (EditText) methodHookParam.thisObject;
                try {
                    if (editText.getHint() != null) {
                        str = editText.getHint().toString().toLowerCase(Locale.ROOT);
                    }
                } catch (Throwable unused) {
                }
                String str2 = str + " " + editText.getClass().getName().toLowerCase(Locale.ROOT);
                Iterator it = AutoLoginHook.USERNAME_FIELD_HINTS.iterator();
                while (it.hasNext()) {
                    if (str2.contains((String) it.next()) && editText.getText().length() == 0) {
                        editText.setText(Config.get().loginEmail);
                        UpdateEventLogger.log("autologin", "filled username on " + editText.getClass().getName());
                        return;
                    }
                }
                Iterator it2 = AutoLoginHook.PASSWORD_FIELD_HINTS.iterator();
                while (it2.hasNext()) {
                    if (str2.contains((String) it2.next()) && editText.getText().length() == 0) {
                        editText.setText(Config.get().loginPassword);
                        UpdateEventLogger.log("autologin", "filled password on " + editText.getClass().getName());
                        return;
                    }
                }
            }
        }
    }

    /* renamed from: com.genspark.updatekiller.hooks.AutoLoginHook$2, reason: invalid class name */
    static class AnonymousClass2 extends XC_MethodHook {
        AnonymousClass2() {
        }

        @Override // de.robv.android.xposed.XC_MethodHook
        protected void afterHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("AutoLoginHook.webviewLoaded", new Guard.Action() { // from class: com.genspark.updatekiller.hooks.AutoLoginHook$2$$ExternalSyntheticLambda0
                @Override // com.genspark.updatekiller.Guard.Action
                public final void run() throws Throwable {
                    AutoLoginHook.AnonymousClass2.lambda$afterHookedMethod$0(methodHookParam);
                }
            });
        }

        static /* synthetic */ void lambda$afterHookedMethod$0(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
            if (methodHookParam.thisObject instanceof WebView) {
                WebView webView = (WebView) methodHookParam.thisObject;
                String valueOf = (methodHookParam.args == null || methodHookParam.args.length <= 0) ? "" : String.valueOf(methodHookParam.args[0]);
                if (valueOf == null) {
                    return;
                }
                String lowerCase = valueOf.toLowerCase(Locale.ROOT);
                if (lowerCase.contains("google") || lowerCase.contains("auth0") || lowerCase.contains("oauth") || lowerCase.contains("embedded") || lowerCase.contains("sign-in")) {
                    return;
                }
                AutoLoginHook.injectLoginJS(webView);
            }
        }
    }

    /* renamed from: com.genspark.updatekiller.hooks.AutoLoginHook$3, reason: invalid class name */
    static class AnonymousClass3 extends XC_MethodHook {
        AnonymousClass3() {
        }

        @Override // de.robv.android.xposed.XC_MethodHook
        protected void beforeHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("AutoLoginHook.flutter", new Guard.Action() { // from class: com.genspark.updatekiller.hooks.AutoLoginHook$3$$ExternalSyntheticLambda0
                @Override // com.genspark.updatekiller.Guard.Action
                public final void run() throws Throwable {
                    AutoLoginHook.AnonymousClass3.lambda$beforeHookedMethod$0(methodHookParam);
                }
            });
        }

        static /* synthetic */ void lambda$beforeHookedMethod$0(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
            String valueOf;
            if (methodHookParam.args == null || methodHookParam.args.length < 1 || (valueOf = String.valueOf(methodHookParam.args[0])) == null) {
                return;
            }
            String lowerCase = valueOf.toLowerCase(Locale.ROOT);
            if (lowerCase.contains("login") || lowerCase.contains("signin")) {
                UxLog.i("AutoLoginHook: Flutter login call intercepted: " + valueOf);
            }
        }
    }

    /* renamed from: com.genspark.updatekiller.hooks.AutoLoginHook$4, reason: invalid class name */
    static class AnonymousClass4 implements Runnable {
        final /* synthetic */ WebView val$web;

        AnonymousClass4(WebView webView) {
            this.val$web = webView;
        }

        @Override // java.lang.Runnable
        public void run() {
            final WebView webView = this.val$web;
            Guard.run("AutoLoginHook.injectJS", new Guard.Action() { // from class: com.genspark.updatekiller.hooks.AutoLoginHook$4$$ExternalSyntheticLambda0
                @Override // com.genspark.updatekiller.Guard.Action
                public final void run() throws Throwable {
                    AutoLoginHook.AnonymousClass4.lambda$run$0(webView);
                }
            });
        }

        static /* synthetic */ void lambda$run$0(WebView webView) throws Throwable {
            webView.evaluateJavascript("(function(){  try{    var inputs=document.querySelectorAll('input[type=email], input[name=email], input[autocomplete=username]');    if(inputs.length){ inputs[0].value='" + Config.get().loginEmail + "'; inputs[0].dispatchEvent(new Event('input')); }    var pwds=document.querySelectorAll('input[type=password]');    if(pwds.length){ pwds[0].value='" + Config.get().loginPassword + "'; pwds[0].dispatchEvent(new Event('input')); }  }catch(e){}})();", null);
            UpdateEventLogger.log("autologin", "JS injected into " + webView.getUrl());
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void injectLoginJS(WebView webView) {
        try {
            new Handler(Looper.getMainLooper()).postDelayed(new AnonymousClass4(webView), Config.get().autoLoginDelayMs);
        } catch (Throwable th) {
            Guard.record("AutoLoginHook.injectJS-post", th);
        }
    }
}
