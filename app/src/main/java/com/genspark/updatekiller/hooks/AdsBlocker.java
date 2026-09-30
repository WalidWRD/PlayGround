package com.genspark.updatekiller.hooks;

import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.UpdateEventLogger;
import com.genspark.updatekiller.UxLog;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;

/**
 * جديد 1.4.0 — مانع الإعلانات لـ Genspark.
 *
 * الدليل المباشر من DEX الهدف: فحوص `strings classes.dex` أظهرت وجود AdMob/Google Ads فعلًا
 *   com.google.android.gms.ads (3 مراجع)
 *   INTERSTITIAL, BANNER, admob_app_id, adunit_exposure, beginAdUnitExposure
 *   com.google.android.gms.ads.identifier.AdvertisingIdClient
 * وبالإضافة لأي شبكة إعلانات تستدعي تحميل HTML داخل WebView.
 *
 * الاستراتيجيات (كلها تُلَفّ داخل Guard، وإلغاء إعلان لا يُنتج استثناء):
 *   (أ) طبقة SDK: نستبدل View المُعاد من أي دالة باسم AdView/AdLoader/InterstitialAd
 *       بـ View فارغ شفّاف — لا خطأ، لا استثناء، ولا استدعاء للإعلان.
 *   (ب) طبقة WebView: مانع العناوين: أي LoadURL/PostURL على نطاقات إعلانية معروفة
 *       يُلغى قبل التنفيذ (يُعدّل الوسيط).
 *
 * مفتاح الإعداد: adsBlock (افتراضي true).
 */
public final class AdsBlocker {

    /** نطاقات يحرّم فتحها (أي ظهور في البيانات أو URL). */
    public static final String[] BLOCKED_HOSTS = {
        "doubleclick.net", "googlesyndication.com", "admob.com", "googleadservices.com",
        "pagead2.googlesyndication.com", "adservice.google.com", "ads.google.com",
        "adsrvr.org", "adnxs.com", "criteo.com", "criteo.net", "amazon-adsystem.com",
        "rubiconproject.com", "pubmatic.com", "openx.net", "casalemedia.com",
        "indexexchange.com", "yieldmo.com", "taboola.com", "outbrain.com",
        "moatads.com", "scorecardresearch.com", "adsymptotic.com", "adroll.com"
    };

    /** أسماء أصناف AdMob الكلاسيكي (والطبقات الداخلية إن وُجدت). */
    public static final String[] AD_CLASSES = {
        "com.google.android.gms.ads.AdView",
        "com.google.android.gms.ads.AdLoader",
        "com.google.android.gms.ads.InterstitialAd",
        "com.google.android.gms.ads.rewarded.RewardedAd",
        "com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd",
        "com.google.android.gms.ads.appopen.AppOpenAd",
        "com.google.android.gms.ads.nativead.NativeAdView",
        "com.google.android.gms.ads.interstitial.InterstitialAd"
    };

    private AdsBlocker() { }

    public static int install(ClassLoader cl) {
        if (!Config.get().adsBlock) { UxLog.i("AdsBlocker: disabled by config"); return 0; }
        int n = 0;

        // (أ) SDK: اعتراض مُنشئات أصناف الإعلانات — نُرجع null آمنًا
        // لكن قبل ذلك نُلوّي الـthisObject بأن View فارغ شفّاف لتجنّب أحكام null في ليوترات التطبيق
        for (String cn : AD_CLASSES) {
            Class<?> c = Reflect.findClass(cn, cl);
            if (c == null) continue;
            n += Reflect.hookAllNamed(c, "<init>", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Guard.run("AdsBlocker.<init>:" + cn, () -> neutralize(p));
                }
            });
            n += Reflect.hookAllNamed(c, "loadAd", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    Guard.run("AdsBlocker.loadAd:" + cn, () -> {
                        p.setResult(null); // يمنع بدء التحميل
                        UpdateEventLogger.log("ads", "blocked loadAd on " + cn);
                        UxLog.i("AdsBlocker: loadAd blocked on " + cn);
                    });
                }
            });
        }
        // إخفاء instances موجودة
        Class<?> adView = Reflect.findClass("com.google.android.gms.ads.AdView", cl);
        if (adView != null) {
            n += Reflect.hookAllNamed(adView, "setVisibility", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    Guard.run("AdsBlocker.setVisibility", () -> {
                        if (p.args != null && p.args.length >= 1 && p.args[0] instanceof Integer) {
                            // نُجبر أي إعلان وُجد مسبقًا على GONE
                            p.args[0] = Integer.valueOf(View.GONE);
                        }
                    });
                }
            });
        }

        // (ب) WebView: الفلترة على مستوى الـloadUrl/PostUrl (مُكمّل لـ WebViewHook)
        Class<?> wv = Reflect.findClass("android.webkit.WebView", cl);
        if (wv != null) {
            n += Reflect.hookBySignature(wv, new String[]{"loadUrl"}, new String[]{"java.lang.String"}, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    Guard.run("AdsBlocker.webView.loadUrl", () -> {
                        String url = (p.args != null && p.args.length >= 1) ? String.valueOf(p.args[0]) : null;
                        if (isAdUrl(url)) {
                            // نُحوّلها إلى صفحة فارغة من البيانات — لا استعلام شبكة
                            p.args[0] = "about:blank";
                            UpdateEventLogger.log("ads", "blocked webview url: " + url);
                            UxLog.i("AdsBlocker: webview ad url → about:blank (was " + url + ")");
                        }
                    });
                }
            });
            n += Reflect.hookBySignature(wv, new String[]{"postUrl"}, new String[]{"java.lang.String","byte[]"}, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    Guard.run("AdsBlocker.webView.postUrl", () -> {
                        String url = (p.args != null && p.args.length >= 1) ? String.valueOf(p.args[0]) : null;
                        if (isAdUrl(url)) {
                            p.args[0] = "about:blank";
                            if (p.args.length >= 2) p.args[1] = new byte[0];
                            UpdateEventLogger.log("ads", "blocked postUrl to " + url);
                            UxLog.i("AdsBlocker: postUrl blocked → about:blank");
                        }
                    });
                }
            });
        }

        UxLog.i("AdsBlocker: " + n + " hook(s) — " + AD_CLASSES.length + " ad classes, " + BLOCKED_HOSTS.length + " host patterns");
        UpdateEventLogger.log("session", "AdsBlocker active: " + n + " hook(s), " + BLOCKED_HOSTS.length + " host patterns");
        return n;
    }

    /** يُحيّد أي View إعلان بأن يختفي ويصير أصغر ما يمكن — آمن حتى داخل ViewGroup الأصل. */
    private static void neutralize(MethodHookParam p) {
        Object v = p.thisObject;
        if (v instanceof View) {
            View view = (View) v;
            try {
                view.setVisibility(View.GONE);
                ViewGroup.LayoutParams lp = view.getLayoutParams();
                if (lp != null) {
                    lp.width = 0; lp.height = 0;
                    view.setLayoutParams(lp);
                }
                ViewGroup parent = (ViewGroup) view.getParent();
                if (parent != null) parent.removeView(view);
                UpdateEventLogger.log("ads", "neutralized " + v.getClass().getName());
                UxLog.i("AdsBlocker: neutralized " + v.getClass().getName());
            } catch (Throwable t) { /* تجنّب الانهيار داخل التطبيق */ }
        }
    }

    public static boolean isAdUrl(String url) {
        if (url == null || url.isEmpty()) return false;
        try {
            for (String h : BLOCKED_HOSTS) if (url.contains(h)) return true;
            return false;
        } catch (Throwable t) { return false; }
    }
}
