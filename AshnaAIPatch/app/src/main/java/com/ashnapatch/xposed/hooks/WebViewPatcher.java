package com.ashnapatch.xposed.hooks;

import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.ashnapatch.xposed.PrefsManager;
import com.ashnapatch.xposed.util.LogUtil;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * إخفاء حوار التحديث وعناصر الإعلانات داخل WebView — نسخة SPA-proof.
 *
 * المشكلة المحلولة: تطبيق الويب (app.ashna.ai) صفحة واحدة SPA — onPageFinished
 * يُستدعى مرة واحدة لكن الحوارات تُبنى لاحقاً عبر JS routing. لذلك:
 * 1) حقن متكرر (فوري + 800ms + 2500ms) بعد كل تحميل/تقدّم 100%.
 * 2) MutationObserver داخل الصفحة يخفي أي عنصر جديد يطابق المحددات أو النص.
 * 3) إخفاء بالنص (عربي/إنجليزي) للأزرار/المودالات + إخفاء بالمحددات للبانرات.
 * يستخدم WebViewClient framework فقط — بلا أسماء كلاسات تطبيق ثابتة.
 */
public final class WebViewPatcher {
    private WebViewPatcher() {}

    private static final String HIDE_JS =
            "(function(){try{"
            + "var SELS=['[id*=update-modal]','[class*=update-modal]',"
            + "'[id*=force-update]','[class*=force-update]',"
            + "'[id*=app-update]','[class*=app-update]',"
            + "'[id*=ad-banner]','[class*=ad-banner]',"
            + "'[id*=ads-container]','[class*=ads-container]',"
            + "'ins.adsbygoogle','.advertisement','[id*=popup-promo]'];"
            + "var WORDS=['update','force update','update required','update now',"
            + "'\\u062a\\u062d\\u062f\\u064a\\u062b','\\u0627\\u062c\\u0628\\u0627\\u0631\\u064a',"
            + "'\\u0627\\u0644\\u0632\\u0627\\u0645\\u064a','\\u0641\\u0648\\u0631\\u064a'];"
            + "function hideBySel(){try{SELS.forEach(function(s){"
            + "document.querySelectorAll(s).forEach(function(e){"
            + "e.style.display='none';e.style.visibility='hidden';});});}catch(e){}}"
            + "function hideByText(){try{"
            + "var els=document.querySelectorAll('div,section,button,[role=dialog],[role=alertdialog]');"
            + "for(var i=0;i<els.length&&i<400;i++){try{var t=(els[i].innerText||'').toLowerCase();"
            + "if(!t||t.length>300)continue;"
            + "for(var j=0;j<WORDS.length;j++){if(t.indexOf(WORDS[j])>=0){"
            + "var n=els[i];for(var k=0;k<4&&n;n=n.parentElement,k++){}"
            + "var m=els[i].closest('[role=dialog],[role=alertdialog],.modal,.overlay')||els[i];"
            + "m.style.display='none';m.style.visibility='hidden';break;}}}catch(e){}}"
            + "}catch(e){}}"
            + "function sweep(){hideBySel();hideByText();}"
            + "sweep();"
            + "try{if(!window.__ashnaPatchObs){"
            + "var ob=new MutationObserver(function(){try{sweep();}catch(e){}});"
            + "ob.observe(document.documentElement||document.body,{childList:true,subtree:true});"
            + "window.__ashnaPatchObs=ob;}}catch(e){}"
            + "}catch(e){}})();";

    public static void apply() {
        try {
            XposedBridge.hookAllMethods(WebViewClient.class, "onPageFinished",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                if (param.args == null || param.args.length < 1) return;
                                if (!(param.args[0] instanceof WebView)) return;
                                injectRepeated((WebView) param.args[0]);
                            } catch (Throwable t) {
                                LogUtil.warn("onPageFinished body failed", t);
                            }
                        }
                    });
            LogUtil.info("WebView JS-hide: onPageFinished hooked.");
        } catch (Throwable t) {
            LogUtil.warn("WebViewPatcher page hook failed", t);
        }
        try {
            // إعادة الحقن عند اكتمال أي تقدّم (تنقّلات SPA الجزئية).
            XposedBridge.hookAllMethods(WebViewClient.class, "onProgressChanged",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                if (param.args == null || param.args.length < 2) return;
                                if (!(param.args[0] instanceof WebView)) return;
                                if (!(param.args[1] instanceof Integer)) return;
                                if (((Integer) param.args[1]).intValue() == 100) {
                                    injectRepeated((WebView) param.args[0]);
                                }
                            } catch (Throwable t) {
                                LogUtil.warn("onProgressChanged body failed", t);
                            }
                        }
                    });
            LogUtil.info("WebView JS-hide: onProgressChanged hooked.");
        } catch (Throwable t) {
            LogUtil.warn("WebViewPatcher progress hook failed", t);
        }
    }

    /** حقن فوري + متكرر لالتقاط عناصر SPA المتأخرة — يحترم مفتاح المستخدم. */
    private static void injectRepeated(final WebView wv) {
        try {
            if (!PrefsManager.getJsHide()) return;
        } catch (Throwable ignored) {
            return;
        }
        injectOnce(wv, 0);
        injectOnce(wv, 800);
        injectOnce(wv, 2500);
    }

    private static void injectOnce(final WebView wv, long delayMs) {
        try {
            wv.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (!PrefsManager.getJsHide()) return;
                        wv.evaluateJavascript(HIDE_JS, null);
                    } catch (Throwable t) {
                        LogUtil.warn("JS inject failed", t);
                    }
                }
            }, delayMs);
        } catch (Throwable t) {
            LogUtil.warn("JS postDelayed failed", t);
        }
    }
}
