package com.apphook.log.hooks;

import android.content.Context;
import android.webkit.CookieManager;
import android.webkit.WebView;

import com.apphook.log.Config;
import com.apphook.log.SafeHook;

/**
 * تدوير الحسابات عند الاستنفاد: كشف رسالة النفاد في الشبكة + مسح الجلسة.
 * (لا Xposed هنا — WebView/CookieManager فقط.)
 */
public final class AccountRotationHook {
    private AccountRotationHook() {}

    private static final String TAG = "Rotation";

    /** JS يكتشف رسالة استنفاد الرصيد */
    public static final String JS_DETECTOR =
        "(function(){" +
        "if(window.__rot)return;window.__rot=1;" +
        "function L(m){try{console.log('[ROTATE] '+String(m));}catch(e){}}" +
        "L('rotation detector active');" +
        "var RE=/(استنفدت|insufficient|exhausted|quota_exceeded|no_credits|not_enough)/i;" +
        "var _f=window.fetch;" +
        "if(_f){window.fetch=function(url,opts){" +
        "  return _f.apply(this,arguments).then(function(r){" +
        "    try{" +
        "      var u=(typeof url==='string')?url:(url&&url.url)||'';" +
        "      if(!/\\/api\\//i.test(u))return r;" +
        "      return r.clone().text().then(function(t){" +
        "        if(RE.test(t)){L('EXHAUSTED at '+u);}" +
        "        return r;" +
        "      }).catch(function(){return r;});" +
        "    }catch(e){return r;}" +
        "  });" +
        "};}" +
        "})();";

    private static volatile String nextToken = null;

    public static void setNextToken(String t) { nextToken = t; }
    public static String getNextToken() { return nextToken; }

    public static void injectDetector(WebView v) {
        try {
            v.loadUrl("javascript:" + JS_DETECTOR);
        } catch (Throwable ignored) {}
    }

    /**
     * تبديل الجلسة الحالية بحساب جديد.
     */
    public static void rotate(Context ctx, WebView webView) {
        if (!Config.ENABLE_ROTATION) return;
        SafeHook.log(TAG + ": rotation starting");

        try {
            // 1. امسح Cookies
            CookieManager cm = CookieManager.getInstance();
            cm.removeAllCookies(null);
            cm.flush();

            // 2. امسح LocalStorage + SessionStorage + Cookies + IndexedDB
            if (webView != null) {
                webView.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String js =
                                "(function(){" +
                                "try{localStorage.clear();}catch(e){}" +
                                "try{sessionStorage.clear();}catch(e){}" +
                                "try{" +
                                "  document.cookie.split(';').forEach(function(c){" +
                                "    document.cookie=c.replace(/^ +/,'').replace(/=.*/,'=;expires='+new Date().toUTCString()+';path=/');" +
                                "  });" +
                                "}catch(e){}" +
                                "try{if(window.indexedDB&&indexedDB.databases){" +
                                "  indexedDB.databases().then(function(dbs){" +
                                "    dbs.forEach(function(db){indexedDB.deleteDatabase(db.name);});" +
                                "  });" +
                                "}}catch(e){}" +
                                "console.log('[ROTATE] storage cleared');" +
                                "})();";
                            webView.loadUrl("javascript:" + js);
                        } catch (Throwable ignored) {}
                    }
                });
            }

            SafeHook.countOpen("account_rotation", "F008");
            SafeHook.log(TAG + ": storage cleared – app restart recommended");
        } catch (Throwable t) {
            SafeHook.logError(TAG + ": rotation failed", t);
        }
    }
}
