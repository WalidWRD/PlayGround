package com.apphook.log.hooks;

import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.apphook.log.Config;
import com.apphook.log.FingerprintEngine;
import com.apphook.log.ReflectCall;
import com.apphook.log.SafeHook;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Features #7-9: WebView — حقن loadUrl بشرائح ≤1700 حرف (مُثبت أنه يعمل
 * على InAppWebView حيث evaluateJavascript صامت الفشل).
 * ANALYZE: تسجيل fetch/XHR فقط. PATCH: + ترقيع fetch/XHR/DOM.
 */
public final class WebViewHook {
    private WebViewHook() {}

    private static final String TAG = "WebView";
    private static final Set<String> injected =
            Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> chunked =
            Collections.synchronizedSet(new HashSet<String>());

    static final int CHUNK_LIMIT = 1700;
    private static volatile List<String> chunksCache;

    /**
     * جدول القواعد الوحيد لـ JS (مرآة SubscriptionPatch — نفس القيم).
     * يُستخدم لبناء الشرائح. المصدر الوحيد — لا تضف قواعد elsewhere.
     */
    static final String[][] JS_RULES = {
        {"\"subType\":\\s*\"free\"", "\"subType\":\"plus\""},
        {"\"subStatus\":\\s*\"never_purchased\"", "\"subStatus\":\"active\""},
        {"\"subStatus\":\\s*\"canceled\"", "\"subStatus\":\"active\""},
        {"\"subStatus\":\\s*\"expired\"", "\"subStatus\":\"active\""},
        {"\"membershipType\":\\s*\"free\"", "\"membershipType\":\"plus\""},
        {"\"membershipStatus\":\\s*\"never_purchased\"", "\"membershipStatus\":\"active\""},
        {"\"membershipStatus\":\\s*\"canceled\"", "\"membershipStatus\":\"active\""},
        {"\"membership_type\":\\s*\"free\"", "\"membership_type\":\"plus\""},
        {"\"personal_membership_type\":\\s*\"free\"", "\"personal_membership_type\":\"plus\""},
        {"\"personal_paid_membership_type\":\\s*\"free\"", "\"personal_paid_membership_type\":\"plus\""},
        {"\"web_membership_type\":\\s*\"free\"", "\"web_membership_type\":\"plus\""},
        {"\"web_paid_membership_type\":\\s*\"free\"", "\"web_paid_membership_type\":\"plus\""},
        {"\"android_paid_membership_type\":\\s*null", "\"android_paid_membership_type\":\"plus\""},
        {"\"apple_paid_membership_type\":\\s*null", "\"apple_paid_membership_type\":\"plus\""},
        {"\"web_subscription_status\":\\s*null", "\"web_subscription_status\":\"active\""},
        {"\"web_subscription_id\":\\s*null", "\"web_subscription_id\":\"sub_apphook_demo\""},
        {"\"vip_membership_type\":\\s*null", "\"vip_membership_type\":\"plus\""},
        {"\"status\":\\s*\"never_purchased\"", "\"status\":\"active\""},
        {"\"is_active\":\\s*false", "\"is_active\":true"},
        {"\"first_paid_at\":\\s*null", "\"first_paid_at\":\"2026-10-15T00:00:00Z\""},
        {"\"current_period_start\":\\s*null", "\"current_period_start\":\"2026-10-15T00:00:00Z\""},
        {"\"current_period_end\":\\s*null", "\"current_period_end\":\"2027-10-15T00:00:00Z\""},
        {"\"effective_current_period_since\":\\s*null", "\"effective_current_period_since\":\"2026-10-15T00:00:00Z\""},
        {"\"plan\":\\s*\"free\"", "\"plan\":\"plus\""},
        {"\"sub_type\":\\s*\"free\"", "\"sub_type\":\"plus\""},
        {"\"user_plan\":\\s*\"free\"", "\"user_plan\":\"plus\""},
        {"\"personal_plan\":\\s*\"free\"", "\"personal_plan\":\"plus\""},
        {"\"org_plan\":\\s*\"\"", "\"org_plan\":\"plus\""},
        {"\"org_active_membership_type\":\\s*\"free\"", "\"org_active_membership_type\":\"plus\""},
        {"\"org_plan_status\":\\s*\"\"", "\"org_plan_status\":\"active\""},
        {"\"member_generation\":\\s*null", "\"member_generation\":\"plus\""},
        {"\"balance\":\\s*[0-9]+", "\"balance\":9999999"},
        {"\"credit_balance\":\\s*[0-9]+", "\"credit_balance\":9999999"},
        {"\"creditBalance\":\\s*[0-9]+", "\"creditBalance\":9999999"},
        {"\"credits\":\\s*[0-9]+(?=[,}])", "\"credits\":9999999"},
        {"\"credit\":\\s*[0-9]+", "\"credit\":9999999"},
        {"\"credit_quota\":\\s*[0-9]+", "\"credit_quota\":9999999"},
        {"\"creditQuota\":\\s*[0-9]+", "\"creditQuota\":9999999"},
        {"\"remaining\":\\s*[0-9]+", "\"remaining\":9999999"},
        {"\"remaining_balance\":\\s*[0-9]+", "\"remaining_balance\":9999999"},
        {"\"remaining_credits\":\\s*[0-9]+", "\"remaining_credits\":9999999"},
        {"\"available\":\\s*[0-9]+", "\"available\":9999999"},
        {"\"vm_quota_lite\":\\s*[0-9]+", "\"vm_quota_lite\":999"},
        {"\"vm_quota_standard\":\\s*[0-9]+", "\"vm_quota_standard\":999"},
        {"\"vm_quota_powerful\":\\s*[0-9]+", "\"vm_quota_powerful\":999"},
        {"\"vm_quota_team\":\\s*[0-9]+", "\"vm_quota_team\":999"},
        {"\"pool_funded\":\\s*false", "\"pool_funded\":true"},
        {"\"org_credit_quota_available\":\\s*false", "\"org_credit_quota_available\":true"},
        {"\"has_team_claw\":\\s*false", "\"has_team_claw\":true"},
        {"\"isPremium\":\\s*false", "\"isPremium\":true"},
        {"\"is_premium\":\\s*false", "\"is_premium\":true"},
        {"\"isVip\":\\s*false", "\"isVip\":true"},
        {"\"is_vip\":\\s*false", "\"is_vip\":true"},
        {"\"isPro\":\\s*false", "\"isPro\":true"},
        {"\"is_pro\":\\s*false", "\"is_pro\":true"},
        {"\"isSubscribed\":\\s*false", "\"isSubscribed\":true"},
        {"\"is_subscribed\":\\s*false", "\"is_subscribed\":true"},
        {"\"premium\":\\s*false", "\"premium\":true"},
        {"\"vip\":\\s*false", "\"vip\":true"},
        {"\"paid\":\\s*false", "\"paid\":true"},
        {"\"tier\":\\s*\"free\"", "\"tier\":\"plus\""},
        {"\"tier\":\\s*\"basic\"", "\"tier\":\"plus\""},
        {"\"subscription_tier\":\\s*\"free\"", "\"subscription_tier\":\"plus\""},
        {"\"subscriptionTier\":\\s*\"free\"", "\"subscriptionTier\":\"plus\""},
        {"\"membership_status\":\\s*\"free\"", "\"membership_status\":\"plus\""},
    };

    public static void install(XposedInterface fw, ClassLoader cl) {
        hookPageStarted(fw);
        hookPageFinished(fw);
        hookConsole(fw);
        hookSetChromeClient(fw);
        SafeHook.log(TAG + ": hooked (patch=" + Config.PATCH_WEBVIEW + ")");
    }

    // ── بناء الشرائح ──

    static String chunkShell(String id, String body, String ready) {
        return "javascript:(function(){"
                + "if(window.__saC" + id + ")return;window.__saC" + id + "=1;"
                + "function L(m){try{console.log('[SA] '+String(m).substring(0,500));}catch(e){}}"
                + body + "L('v6.0 " + ready + "');})();";
    }

    public static List<String> patchChunks() {
        List<String> c = chunksCache;
        if (c != null) return c;
        synchronized (WebViewHook.class) {
            c = chunksCache;
            if (c == null) {
                c = buildChunks();
                chunksCache = c;
            }
            return c;
        }
    }

    private static String chunkBootstrap() {
        return "javascript:(function(){"
                + "if(window.__sa60){return;}window.__sa60=true;"
                + "function L(m){try{console.log('[SA] '+String(m).substring(0,500));}catch(e){}}"
                + "window.__saL=L;"
                + "var R=/\\/api\\/(user|payment|quota|review_reward|subscri|member|balance|credit|premium)/i;"
                + "window.__saR=R;"
                + "window.__saP=function(t){if(typeof t!=='string'||t.length<2)return t;return t;};"
                + "L('v6.0 P1 loaded');})();";
    }

    private static String chunkRules(String id, String stmts) {
        return chunkShell(id,
                "try{var _p=window.__saP||function(t){return t;};"
                + "window.__saP=function(t){var p;try{p=_p(t);}catch(e){p=t;}"
                + stmts + "return p;};}catch(e){}",
                "rules ready");
    }

    static List<String> buildChunks() {
        List<String> out = new ArrayList<>();
        out.add(chunkBootstrap());
        StringBuilder cur = new StringBuilder();
        int idx = 0;
        for (String[] r : JS_RULES) {
            String stmt = "p=p.replace(/" + r[0] + "/g,'" + r[1] + "');";
            if (cur.length() + stmt.length() > 950) {
                out.add(chunkRules(String.valueOf(idx++), cur.toString()));
                cur.setLength(0);
            }
            cur.append(stmt);
        }
        if (cur.length() > 0) out.add(chunkRules(String.valueOf(idx++), cur.toString()));
        out.addAll(chunkFinals());
        return out;
    }

    private static List<String> chunkFinals() {
        List<String> out = new ArrayList<>();
        String fetchBody = "try{if(window.fetch){var _f=window.fetch;"
                + "window.fetch=function(){var a=arguments;"
                + "var u=(typeof a[0]==='string')?a[0]:((a[0]&&a[0].url)||'');"
                + "var sens=window.__saR?window.__saR.test(u):false;"
                + "if(sens){window.__saL('fetch* '+u);}"
                + "try{return _f.apply(this,a).then(function(r){if(!sens){return r;}"
                + "try{return r.clone().text().then(function(t){"
                + "window.__saL('resp['+u+']='+t.substring(0,600));"
                + "var q=window.__saP(t);"
                + "if(q!==t){window.__saL('PATCHED['+u+']');"
                + "var h=new Headers();"
                + "try{r.headers.forEach(function(v,k){var lk=k.toLowerCase();"
                + "if(lk!=='content-length'&&lk!=='content-encoding')h.set(k,v);});}catch(e){}"
                + "return new Response(q,{status:r.status,statusText:r.statusText,headers:h});}"
                + "return r;}).catch(function(e){return r;});}"
                + "catch(e){return r;}});}"
                + "catch(e){return _f.apply(this,a);}};"
                + "}else{window.__saL('no fetch');}}catch(e){}";
        String xhrBody = "try{if(window.XMLHttpRequest){var _X=window.XMLHttpRequest;"
                + "var _op=_X.prototype.open;var _sd=_X.prototype.send;"
                + "_X.prototype.open=function(m,u){try{this.__au=u;}catch(e){}return _op.apply(this,arguments);};"
                + "_X.prototype.send=function(){var self=this;"
                + "var sens=window.__saR?window.__saR.test(self.__au||''):false;"
                + "if(sens){try{self.addEventListener('readystatechange',function(){"
                + "if(self.readyState===4){try{"
                + "var o=self.responseText||'';"
                + "window.__saL('xhr['+self.__au+'] s='+self.status);"
                + "var q=window.__saP(o);"
                + "if(q!==o){window.__saL('XPATCHED['+self.__au+']');"
                + "try{Object.defineProperty(self,'responseText',{value:q,configurable:true});}catch(e){}"
                + "try{Object.defineProperty(self,'response',{value:q,configurable:true});}catch(e){}}"
                + "}catch(e){}}});}catch(e){}}"
                + "return _sd.apply(this,arguments);};"
                + "}else{window.__saL('no xhr');}}catch(e){}";
        out.add(chunkShell("F", fetchBody, "fetch hooked"));
        out.add(chunkShell("X", xhrBody, "xhr hooked"));
        if (Config.PATCH_WEBVIEW) {
            out.add(chunkShell("D", domPatchJs(), "dom ready"));
        }
        return out;
    }

    /** مراقب DOM + إخفاء رسائل النفاد (PATCH فقط). */
    static String domPatchJs() {
        return "var LG=window.__saL||function(m){try{console.log('[SA] '+m);}catch(e){}};"
                + "function fixDom(){try{"
                + "var els=document.querySelectorAll('*');"
                + "for(var i=0;i<els.length;i++){var el=els[i];"
                + "if(el.children.length>0)continue;"
                + "var t=(el.textContent||'').trim();if(!t)continue;"
                + "if(/^[0-9]{1,6}([.,][0-9]+)?$/.test(t)){"
                + "var n=parseFloat(t.replace(/,/g,''));"
                + "if(n<1000000){var ctx='';var p=el;var d=0;"
                + "while(p&&d<5){ctx+=' '+(p.textContent||'');p=p.parentElement;d++;}"
                + "if(/credit|balance|Credits|رصيد|كريدت/i.test(ctx)){"
                + "el.textContent='9999999';LG('DOM fix: '+t);}}}"
                + "if(/غير\\s*كافية|استنفدت|insufficient|exhausted|رصيدك بالكامل|Upgrade to Plus/i.test(t)){"
                + "if(el.style)el.style.display='none';"
                + "var pp=el.parentElement;if(pp&&pp.style)pp.style.display='none';"
                + "LG('DOM hide: '+t.substring(0,30));}"
                + "}}catch(e){}}"
                + "try{var mo=new MutationObserver(function(){setTimeout(fixDom,100);});"
                + "mo.observe(document.body,{childList:true,subtree:true,characterData:true});"
                + "LG('MO active');}catch(e){}"
                + "try{setInterval(fixDom,800);}catch(e){}try{fixDom();}catch(e){}";
    }

    static void injectChunks(Object wv, String phase) {
        if (!(wv instanceof WebView)) return;
        final WebView view = (WebView) wv;
        String key;
        try {
            key = view.getClass().getName() + "@" + System.identityHashCode(view);
        } catch (Throwable ignored) {
            return;
        }
        if (!chunked.add(key)) return;
        final List<String> chunks;
        try {
            chunks = patchChunks();
        } catch (Throwable t) {
            SafeHook.logError(TAG + ".chunks", t);
            return;
        }
        SafeHook.log(TAG + ": injecting " + chunks.size() + " parts (" + phase + ")");
        try {
            view.post(new Runnable() {
                @Override
                public void run() {
                    for (int i = 0; i < chunks.size(); i++) {
                        final String c = chunks.get(i);
                        try {
                            if (i == 0) {
                                view.loadUrl(c);
                            } else {
                                view.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            view.loadUrl(c);
                                        } catch (Throwable ignored) {}
                                    }
                                }, i * 300);
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            });
        } catch (Throwable t) {
            SafeHook.logError(TAG + ".post", t);
        }
        if (chunked.size() > 200) chunked.clear();
    }

    // ── الربط ──

    private static Class<?> sysClass(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void hookPageStarted(XposedInterface fw) {
        Class<?> client = sysClass("android.webkit.WebViewClient");
        if (client == null) return;
        HookKit.hookAll(fw, client, "onPageStarted", 3, 3, TAG + ".started", chain -> {
            try {
                Object wv = chain.getArg(0);
                Object u = chain.getArg(1);
                SafeHook.recordCall(TAG, "onPageStarted", 1, u, u);
                if (wv != null) injectChunks(wv, "start");
            } catch (Throwable t) {
                SafeHook.logError(TAG + ".started", t);
            }
            return chain.proceed();
        });
    }

    private static void hookPageFinished(XposedInterface fw) {
        Class<?> client = sysClass("android.webkit.WebViewClient");
        if (client == null) return;
        HookKit.hookAll(fw, client, "onPageFinished", 2, 2, TAG + ".finished", chain -> {
            try {
                Object wv = chain.getArg(0);
                Object u = chain.getArg(1);
                if (!injected.add("f:" + u)) return chain.proceed();
                SafeHook.recordCall(TAG, "onPageFinished", 1, u, u);
                if (wv != null) injectChunks(wv, "finish");
                if (injected.size() > 200) injected.clear();
            } catch (Throwable t) {
                SafeHook.logError(TAG + ".finished", t);
            }
            return chain.proceed();
        });
    }

    private static void hookConsole(XposedInterface fw) {
        Class<?> chrome = sysClass("android.webkit.WebChromeClient");
        if (chrome == null) return;
        hookConsoleOn(fw, chrome);
    }

    private static void hookSetChromeClient(XposedInterface fw) {
        Class<?> wv = sysClass("android.webkit.WebView");
        if (wv == null) return;
        Method m = FingerprintEngine.findMethodByFingerprint(wv,
                "setWebChromeClient", void.class, 1);
        if (m == null) {
            SafeHook.logSafe(TAG + ".setWebChromeClient", "method-not-found");
            return;
        }
        final Method target = m;
        try {
            fw.hook(target)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        try {
                            Object client = chain.getArg(0);
                            if (client != null) {
                                SafeHook.log(TAG + ": Real ChromeClient = "
                                        + client.getClass().getName());
                                hookConsoleOn(fw, client.getClass());
                            }
                        } catch (Throwable t) {
                            SafeHook.logError(TAG + ".setClient", t);
                        }
                        return chain.proceed();
                    });
            SafeHook.log("Hook OK: WebView.setWebChromeClient -> " + m);
        } catch (Throwable t) {
            SafeHook.logError(TAG + ".setClient", t);
        }
    }

    static void hookConsoleOn(XposedInterface fw, Class<?> clazz) {
        HookKit.hookAll(fw, clazz, "onConsoleMessage", 1, 3, TAG + ".console", chain -> {
            try {
                String text = extractConsole(chain);
                if (text != null && text.startsWith("[SA]")) {
                    SafeHook.recordCall(TAG + ".js", abbrev(text, 80),
                            chain.getArgs().size(), text, text);
                }
            } catch (Throwable t) {
                SafeHook.logError(TAG + ".console", t);
            }
            return chain.proceed();
        });
    }

    private static String extractConsole(XposedInterface.Chain chain) {
        try {
            if (chain.getArgs().size() == 1) {
                Object cm = chain.getArg(0);
                Object msg = ReflectCall.call(cm, "message");
                if (msg != null) return String.valueOf(msg);
                return String.valueOf(cm);
            }
            Object m0 = chain.getArg(0);
            if (m0 instanceof String) return (String) m0;
        } catch (Throwable ignored) {}
        return null;
    }

    private static String abbrev(String s, int max) {
        if (s == null) return "null";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
