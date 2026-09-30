package com.genspark.updatekiller.hooks;

import android.content.Intent;
import android.net.Uri;
import com.genspark.updatekiller.BuildInfo;
import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.UpdateEventLogger;
import com.genspark.updatekiller.UxLog;
import com.genspark.updatekiller.hooks.OfficialSiteRedirectBypass;
import de.robv.android.xposed.XC_MethodHook;

/* loaded from: classes.dex */
public final class OfficialSiteRedirectBypass {
    private OfficialSiteRedirectBypass() {
    }

    public static int install(ClassLoader classLoader) {
        if (!Config.get().bypassOfficialRedirect) {
            UxLog.i("OfficialSiteRedirectBypass: disabled");
            return 0;
        }
        Class<?> findClass = Reflect.findClass("android.content.ContextWrapper", classLoader);
        int hookAllNamed = findClass != null ? 0 + Reflect.hookAllNamed(findClass, "startActivity", new AnonymousClass1()) : 0;
        Class<?> findClass2 = Reflect.findClass("android.webkit.WebView", classLoader);
        if (findClass2 != null) {
            hookAllNamed += Reflect.hookAllNamed(findClass2, "loadUrl", new AnonymousClass2());
        }
        UxLog.i("OfficialSiteRedirectBypass: " + hookAllNamed + " hook(s)");
        return hookAllNamed;
    }

    /* renamed from: com.genspark.updatekiller.hooks.OfficialSiteRedirectBypass$1, reason: invalid class name */
    static class AnonymousClass1 extends XC_MethodHook {
        AnonymousClass1() {
        }

        @Override // de.robv.android.xposed.XC_MethodHook
        protected void beforeHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("OfficialSiteRedirectBypass.intent", new Guard.Action() { // from class: com.genspark.updatekiller.hooks.OfficialSiteRedirectBypass$1$$ExternalSyntheticLambda0
                @Override // com.genspark.updatekiller.Guard.Action
                public final void run() throws Throwable {
                    OfficialSiteRedirectBypass.AnonymousClass1.lambda$beforeHookedMethod$0(methodHookParam);
                }
            });
        }

        static /* synthetic */ void lambda$beforeHookedMethod$0(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
            Intent intent;
            Uri data;
            String host;
            if (methodHookParam.args == null || methodHookParam.args.length < 1) {
                return;
            }
            Object obj = methodHookParam.args[0];
            if (!(obj instanceof Intent) || (data = (intent = (Intent) obj).getData()) == null || (host = data.getHost()) == null || !host.endsWith("genspark.ai") || host.equals("www.genspark.ai")) {
                return;
            }
            intent.setData(Uri.parse(BuildInfo.HOMEPAGE));
            UpdateEventLogger.log("intent", "redirected " + host + " → www.genspark.ai");
            UxLog.i("OfficialSiteRedirectBypass: → www.genspark.ai");
        }
    }

    /* renamed from: com.genspark.updatekiller.hooks.OfficialSiteRedirectBypass$2, reason: invalid class name */
    static class AnonymousClass2 extends XC_MethodHook {
        AnonymousClass2() {
        }

        @Override // de.robv.android.xposed.XC_MethodHook
        protected void beforeHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("OfficialSiteRedirectBypass.webview", new Guard.Action() { // from class: com.genspark.updatekiller.hooks.OfficialSiteRedirectBypass$2$$ExternalSyntheticLambda0
                @Override // com.genspark.updatekiller.Guard.Action
                public final void run() throws Throwable {
                    OfficialSiteRedirectBypass.AnonymousClass2.lambda$beforeHookedMethod$0(methodHookParam);
                }
            });
        }

        static /* synthetic */ void lambda$beforeHookedMethod$0(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
            String valueOf;
            if (methodHookParam.args == null || methodHookParam.args.length < 1 || (valueOf = String.valueOf(methodHookParam.args[0])) == null || !valueOf.contains("genspark.ai") || valueOf.startsWith("https://www.genspark.ai")) {
                return;
            }
            methodHookParam.args[0] = BuildInfo.HOMEPAGE;
        }
    }
}
