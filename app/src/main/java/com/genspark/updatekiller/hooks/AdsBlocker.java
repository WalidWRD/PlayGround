package com.genspark.updatekiller.hooks;

import android.view.View;
import android.view.ViewGroup;
import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.UpdateEventLogger;
import com.genspark.updatekiller.UxLog;
import com.genspark.updatekiller.hooks.AdsBlocker;
import de.robv.android.xposed.XC_MethodHook;

/* loaded from: classes.dex */
public final class AdsBlocker {
    public static final String[] BLOCKED_HOSTS = {"doubleclick.net", "googlesyndication.com", "admob.com", "googleadservices.com", "pagead2.googlesyndication.com", "adservice.google.com", "ads.google.com", "adsrvr.org", "adnxs.com", "criteo.com", "criteo.net", "amazon-adsystem.com", "rubiconproject.com", "pubmatic.com", "openx.net", "casalemedia.com", "indexexchange.com", "yieldmo.com", "taboola.com", "outbrain.com", "moatads.com", "scorecardresearch.com", "adsymptotic.com", "adroll.com"};
    public static final String[] AD_CLASSES = {"com.google.android.gms.ads.AdView", "com.google.android.gms.ads.AdLoader", "com.google.android.gms.ads.InterstitialAd", "com.google.android.gms.ads.rewarded.RewardedAd", "com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd", "com.google.android.gms.ads.appopen.AppOpenAd", "com.google.android.gms.ads.nativead.NativeAdView", "com.google.android.gms.ads.interstitial.InterstitialAd"};

    private AdsBlocker() {
    }

    public static int install(ClassLoader classLoader) {
        if (!Config.get().adsBlock) {
            UxLog.i("AdsBlocker: disabled");
            return 0;
        }
        int i = 0;
        for (String str : AD_CLASSES) {
            Class<?> findClass = Reflect.findClass(str, classLoader);
            if (findClass != null) {
                i += Reflect.hookAllNamed(findClass, "loadAd", new AnonymousClass1(str));
            }
        }
        Class<?> findClass2 = Reflect.findClass("android.webkit.WebView", classLoader);
        if (findClass2 != null) {
            i += Reflect.hookAllNamed(findClass2, "loadUrl", new AnonymousClass2());
        }
        UxLog.i("AdsBlocker: " + i + " hook(s) active, " + BLOCKED_HOSTS.length + " host patterns");
        return i;
    }

    /* renamed from: com.genspark.updatekiller.hooks.AdsBlocker$1, reason: invalid class name */
    static class AnonymousClass1 extends XC_MethodHook {
        final /* synthetic */ String val$cn;

        AnonymousClass1(String str) {
            this.val$cn = str;
        }

        @Override // de.robv.android.xposed.XC_MethodHook
        protected void beforeHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            final String str = this.val$cn;
            Guard.run("AdsBlocker.loadAd", new Guard.Action() { // from class: com.genspark.updatekiller.hooks.AdsBlocker$1$$ExternalSyntheticLambda0
                @Override // com.genspark.updatekiller.Guard.Action
                public final void run() throws Throwable {
                    AdsBlocker.AnonymousClass1.lambda$beforeHookedMethod$0(methodHookParam, str);
                }
            });
        }

        static /* synthetic */ void lambda$beforeHookedMethod$0(XC_MethodHook.MethodHookParam methodHookParam, String str) throws Throwable {
            methodHookParam.setResult(null);
            UpdateEventLogger.log("ads", "loadAd blocked on " + str);
        }
    }

    /* renamed from: com.genspark.updatekiller.hooks.AdsBlocker$2, reason: invalid class name */
    static class AnonymousClass2 extends XC_MethodHook {
        AnonymousClass2() {
        }

        @Override // de.robv.android.xposed.XC_MethodHook
        protected void beforeHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("AdsBlocker.WebView.loadUrl", new Guard.Action() { // from class: com.genspark.updatekiller.hooks.AdsBlocker$2$$ExternalSyntheticLambda0
                @Override // com.genspark.updatekiller.Guard.Action
                public final void run() throws Throwable {
                    AdsBlocker.AnonymousClass2.lambda$beforeHookedMethod$0(methodHookParam);
                }
            });
        }

        static /* synthetic */ void lambda$beforeHookedMethod$0(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
            String valueOf;
            if (methodHookParam.args == null || methodHookParam.args.length < 1 || (valueOf = String.valueOf(methodHookParam.args[0])) == null || !AdsBlocker.isAdUrl(valueOf)) {
                return;
            }
            methodHookParam.setResult(null);
            UxLog.i("AdsBlocker: blocked ad URL " + valueOf);
        }
    }

    public static boolean isAdUrl(String str) {
        if (str != null && !str.isEmpty()) {
            try {
                for (String str2 : BLOCKED_HOSTS) {
                    if (str.contains(str2)) {
                        return true;
                    }
                }
            } catch (Throwable unused) {
            }
        }
        return false;
    }

    public static void neutralize(View view) {
        if (view == null) {
            return;
        }
        try {
            view.setVisibility(8);
            ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
            if (layoutParams != null) {
                layoutParams.width = 0;
                layoutParams.height = 0;
                view.setLayoutParams(layoutParams);
            }
            ViewGroup viewGroup = (ViewGroup) view.getParent();
            if (viewGroup != null) {
                viewGroup.removeView(view);
            }
            UpdateEventLogger.log("ads", "neutralized " + view.getClass().getName());
        } catch (Throwable unused) {
        }
    }
}
