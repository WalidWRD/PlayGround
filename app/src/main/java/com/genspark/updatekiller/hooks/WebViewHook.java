package com.genspark.updatekiller.hooks;

import android.webkit.WebView;
import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.UpdateEventLogger;
import com.genspark.updatekiller.UxLog;
import com.genspark.updatekiller.hooks.WebViewHook;
import de.robv.android.xposed.XC_MethodHook;
import java.util.Locale;

/* loaded from: classes.dex */
public final class WebViewHook {
    private WebViewHook() {
    }

    public static int install(ClassLoader classLoader) {
        if (!Config.get().blockWebViewStore) {
            UxLog.i("WebViewHook: disabled");
            return 0;
        }
        Class<?> findClass = Reflect.findClass("android.webkit.WebView", classLoader);
        if (findClass == null) {
            UxLog.i("WebViewHook: WebView not found");
            return 0;
        }
        int hookAllNamed = Reflect.hookAllNamed(findClass, "loadUrl", new AnonymousClass1()) + 0;
        Class<?> findClass2 = Reflect.findClass("android.webkit.WebViewClient", classLoader);
        if (findClass2 != null) {
            hookAllNamed += Reflect.hookAllNamed(findClass2, "shouldOverrideUrlLoading", new AnonymousClass2());
        }
        UxLog.i("WebViewHook: " + hookAllNamed + " hook(s)");
        return hookAllNamed;
    }

    /* renamed from: com.genspark.updatekiller.hooks.WebViewHook$1, reason: invalid class name */
    static class AnonymousClass1 extends XC_MethodHook {
        AnonymousClass1() {
        }

        @Override // de.robv.android.xposed.XC_MethodHook
        protected void beforeHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("WebViewHook.loadUrl", new Guard.Action() { // from class: com.genspark.updatekiller.hooks.WebViewHook$1$$ExternalSyntheticLambda0
                @Override // com.genspark.updatekiller.Guard.Action
                public final void run() throws Throwable {
                    WebViewHook.AnonymousClass1.lambda$beforeHookedMethod$0(methodHookParam);
                }
            });
        }

        static /* synthetic */ void lambda$beforeHookedMethod$0(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
            String valueOf;
            if (methodHookParam.args == null || methodHookParam.args.length < 1 || (valueOf = String.valueOf(methodHookParam.args[0])) == null) {
                return;
            }
            String lowerCase = valueOf.toLowerCase(Locale.ROOT);
            if (lowerCase.contains("play.google") || lowerCase.contains("market.android")) {
                methodHookParam.args[0] = Config.get().storeRedirectUrl;
                UpdateEventLogger.log("intent", "WebView redirected → " + Config.get().storeRedirectUrl);
                UxLog.i("WebViewHook: redirected store → " + Config.get().storeRedirectUrl);
            }
        }
    }

    /* renamed from: com.genspark.updatekiller.hooks.WebViewHook$2, reason: invalid class name */
    static class AnonymousClass2 extends XC_MethodHook {
        AnonymousClass2() {
        }

        @Override // de.robv.android.xposed.XC_MethodHook
        protected void beforeHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("WebViewHook.shouldOverrideUrlLoading", new Guard.Action() { // from class: com.genspark.updatekiller.hooks.WebViewHook$2$$ExternalSyntheticLambda0
                @Override // com.genspark.updatekiller.Guard.Action
                public final void run() throws Throwable {
                    WebViewHook.AnonymousClass2.lambda$beforeHookedMethod$0(methodHookParam);
                }
            });
        }

        static /* synthetic */ void lambda$beforeHookedMethod$0(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
            if (methodHookParam.args == null || methodHookParam.args.length < 2) {
                return;
            }
            Object obj = methodHookParam.args[0];
            String valueOf = String.valueOf(methodHookParam.args[1]);
            if (valueOf == null) {
                return;
            }
            String lowerCase = valueOf.toLowerCase(Locale.ROOT);
            if (lowerCase.contains("play.google") || lowerCase.contains("market.android")) {
                if (obj instanceof WebView) {
                    ((WebView) obj).loadUrl(Config.get().storeRedirectUrl);
                }
                methodHookParam.setResult(Boolean.TRUE);
                UpdateEventLogger.log("intent", "WebViewClient override → " + Config.get().storeRedirectUrl);
            }
        }
    }
}
