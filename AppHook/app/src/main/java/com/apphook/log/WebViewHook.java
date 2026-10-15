package com.apphook.log;

import android.content.pm.ApplicationInfo;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import dalvik.system.DexFile;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * v4.7 — الإصلاح الحاسم: ربط base {@code WebChromeClient.onConsoleMessage}
 * لا يلتقط شيئاً لأن flutter_inappwebview يسجل subclass مخصصاً
 * ({@code ...InAppWebViewChromeClient}) يُنفّذ onConsoleMessage دون super
 * (مثبت: JS injected + bridge hooked لكن صفر [WebView-JS]).
 * الحل: اعتراض {@code WebView.setWebChromeClient} وربط onConsoleMessage
 * على <b>الفئة الفعلية</b> لحظة تسجيلها + إثبات تنفيذ JS عبر
 * {@code document.title=AH47_...} مع مراقبة {@code getTitle()}
 * (قناة تحقق مستقلة عن console تماماً).
 *
 * <p>كل إصلاحات v4.4-v4.6 مُبقاة. السلامة: post()، حارس
 * {@code __apphook_v47}، لا حظر، PROTECTIVE + dedup.</p>
 */
public final class WebViewHook {
    private WebViewHook() {}

    private static final String TAG = "AppHook.WebView";

    private static final Set<String> hookedClasses =
            Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> injectedViews =
            Collections.synchronizedSet(new HashSet<String>());
    private static final Set<String> injectedPages =
            Collections.synchronizedSet(new HashSet<String>());

    // أسماء واجهة الجسر المحتملة (best-effort — الاكتشاف الديناميكي يغطي الباقي)
    private static final String[] KNOWN_BRIDGES = {
            "com.pichillilorenzo.flutter_inappwebview_android.webview.JavaScriptBridgeInterface",
            "com.pichillilorenzo.flutter_inappwebview_android.in_app_webview.InAppWebViewJavaScriptBridgeInterface",
            "com.pichillilorenzo.flutter_inappwebview_android.JavaScriptBridgeInterface"
    };

    private static final String[] KNOWN_CLIENTS = {
            "com.pichillilorenzo.flutter_inappwebview_android.in_app_webview.InAppWebViewClient",
            "com.pichillilorenzo.flutter_inappwebview_android.in_app_browser.InAppBrowserClient",
            "com.pichillilorenzo.flutter_inappwebview_android.InAppWebViewClient",
            "com.pichillilorenzo.flutter_inappwebview_android.InAppBrowserClient"
    };

    public static void install(XposedInterface fw, ClassLoader cl, ApplicationInfo ai) {
        hookJsBridge(fw);
        hookShouldIntercept(fw, cl);
        hookPageStarted(fw);
        hookPageFinished(fw);
        hookConsole(fw);
        hookSetChromeClient(fw); // v4.7: الفئة الفعلية — الإصلاح الحاسم
        hookGetTitle(fw);        // v4.7: قناة إثبات مستقلة
        hookBridgeLog(fw, cl, ai);
        hookKnownClients(fw, cl);
        hookDiscoveredClients(fw, ai);
    }

    /** توافق قديم: بلا ApplicationInfo */
    public static void install(XposedInterface fw, ClassLoader cl) {
        install(fw, cl, null);
    }

    // ── 1) سكربت الترقيع v4.4 ───────────────────────────────────

    private static volatile String patchJsCache;

    static String patchJs() {
        String c = patchJsCache;
        if (c != null) return c;
        synchronized (WebViewHook.class) {
            c = patchJsCache;
            if (c == null) {
                c = buildPatchJs();
                patchJsCache = c;
            }
            return c;
        }
    }

    private static void addRule(StringBuilder js, String from, String to) {
        js.append("p=p.replace(/").append(from).append("/g,'").append(to).append("');");
    }

    /**
     * جدول القواعد الوحيد لـ JS (57 قاعدة — مرآة SubscriptionPatch).
     * يُستخدم لبناء السكربت الكامل (evaluate fallback) وتقطيعه إلى
     * chunks ≤1700 حرف (loadUrl primary). المصدر الوحيد — لا تضف
     * قواعد في مكان آخر.
     */
    static final String[][] JS_RULES = {
        {"\"subType\":\\s*\"free\"", "\"subType\":\"plus\""},
        {"\"subStatus\":\\s*\"never_purchased\"", "\"subStatus\":\"active\""},
        {"\"subStatus\":\\s*\"canceled\"", "\"subStatus\":\"active\""},
        {"\"subStatus\":\\s*\"expired\"", "\"subStatus\":\"active\""},
        {"\"membershipType\":\\s*\"free\"", "\"membershipType\":\"plus\""},
        {"\"membershipStatus\":\\s*\"never_purchased\"", "\"membershipStatus\":\"active\""},
        {"\"membershipStatus\":\\s*\"canceled\"", "\"membershipStatus\":\"active\""},
        {"\"membership_status\":\\s*\"free\"", "\"membership_status\":\"plus\""},
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
    };

    /** حد loadUrl الآمن (الحد الفعلي ~2000 — هامش أمان) */
    static final int CHUNK_LIMIT = 1700;

    private static volatile java.util.List<String> chunksCache;

    /** شرائح الحقن عبر loadUrl — تُبنى مرة واحدة */
    static java.util.List<String> patchChunks() {
        java.util.List<String> c = chunksCache;
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

    private static String chunkShell(String id, String body, String ready) {
        return "javascript:(function(){"
                + "if(window.__ahC" + id + ")return;window.__ahC" + id + "=1;"
                + "function L(m){try{console.log('[AppHook] '+String(m).substring(0,500));}catch(e){}}"
                + body
                + "L('v5.1 " + ready + "');"
                + "})();";
    }

    /** chunk التمهيد: الحارس + L + R + قاعدة __ahP الفارغة */
    private static String chunkBootstrap() {
        return "javascript:(function(){"
                + "if(window.__apphook_v51){return;}window.__apphook_v51=true;"
                + "function L(m){try{console.log('[AppHook] '+String(m).substring(0,500));}catch(e){}}"
                + "window.__ahL=L;"
                + "var R=/\\/api\\/(user|payment\\/current_subscriptions|payment\\/get_credit_balance|quota\\/plan_usage|payment\\/sub2\\/tier_config|payment\\/sub2\\/pending_schedule|payment\\/add_tracking_event|review_reward)/i;"
                + "window.__ahR=R;"
                + "window.__ahP=function(t){if(typeof t!=='string'||t.length<2)return t;return t;};"
                + "L('v5.1 P1 loaded');"
                + "})();";
    }

    /** chunk قواعد تراكمي: يُغلّف __ahP السابق ويضيف دفعة */
    private static String chunkRules(String id, String stmts) {
        return chunkShell(id,
                "try{var _p=window.__ahP||function(t){return t;};"
                + "window.__ahP=function(t){var p;try{p=_p(t);}catch(e){p=t;}"
                + stmts + "return p;};}catch(e){}",
                "rules ready");
    }

    /**
     * v5.1 — NUXT hydration patcher: تطبيق Nuxt يقرأ state مُخزّناً
     * (window.__NUXT__ / #__NUXT_DATA__) مرة واحدة عند التحميل — قبل
     * أن يعمل patch الـ fetch. هذا يُرقّع الكائن الحي + عنصر البيانات.
     */
    static String nuxtJs() {
        return "function patchObj(o,d){d=d||0;if(d>8||!o||typeof o!=='object')return;"
                + "for(var k in o){try{var v=o[k];"
                + "if(typeof v==='string'&&/^(free|never_purchased)$/i.test(v)"
                + "&&/plan|member|sub|status|tier/i.test(k)){o[k]='plus';LG('NUXT plan.'+k);}"
                + "else if(typeof v==='string'&&/^credit|balance|remaining|available/i.test(k)){"
                + "var n=parseInt(v);if(!isNaN(n)&&n<1000000){o[k]='9999999';LG('NUXT num.'+k);}}"
                + "else if(typeof v==='number'&&/credit|balance|remaining|available|quota/i.test(k)){"
                + "if(v<1000000){o[k]=9999999;LG('NUXT num.'+k);}}"
                + "else if(typeof v==='object'&&v!==null){patchObj(v,d+1);}"
                + "}catch(e){}}}"
                + "function patchNuxt(){"
                + "try{if(window.__NUXT__){patchObj(window.__NUXT__);LG('NUXT patched');}}catch(e){}"
                + "try{var el=document.getElementById('__NUXT_DATA__');"
                + "if(el&&el.textContent){var txt=el.textContent;"
                + "var fixed=txt.replace(/\"free\"/g,'\"plus\"')"
                + ".replace(/\"balance\",\\s*\\d+/g,'\"balance\",9999999')"
                + ".replace(/\"credit_balance\",\\s*\\d+/g,'\"credit_balance\",9999999');"
                + "if(fixed!==txt){el.textContent=fixed;LG('NUXT_DATA patched');}}}catch(e){}}"
                + "try{setInterval(patchNuxt,2000);LG('nuxt watcher on');}catch(e){}"
                + "try{patchNuxt();}catch(e){}";
    }

    /**
     * v5.2 — DOM fixer: MutationObserver حقيقي + بحث 5 مستويات لأعلى +
     * أرقام عشرية + إخفاء "Upgrade to Plus". يغطي شاشات WebView التي
     * تُرسم قبل الحقن أو من state قديم.
     */
    static String domJs() {
        return "function fixDom(){try{"
                + "var els=document.querySelectorAll('*');"
                + "for(var i=0;i<els.length;i++){var el=els[i];"
                + "if(el.children.length>0)continue;"
                + "var t=(el.textContent||'').trim();if(!t)continue;"
                + "if(/^[0-9]{1,6}([.,][0-9]+)?$/.test(t)){"
                + "var n=parseFloat(t.replace(/,/g,''));"
                + "if(n<1000000){"
                + "var ctx='';var p=el;var d=0;"
                + "while(p&&d<5){ctx+=' '+(p.textContent||'');p=p.parentElement;d++;}"
                + "if(/credit|balance|Credits|رصيد|كريدت/i.test(ctx)){"
                + "el.textContent='9999999';LG('DOM fix: '+t);}}}"
                + "if(/غير\\s*كافية|استنفدت|insufficient|exhausted|رصيدك بالكامل|Upgrade to Plus/i.test(t)){"
                + "if(el.style)el.style.display='none';"
                + "var pp=el.parentElement;if(pp&&pp.style)pp.style.display='none';"
                + "LG('DOM hide: '+t.substring(0,30));}"
                + "}}catch(e){}}"
                + "try{"
                + "var mo=new MutationObserver(function(){setTimeout(fixDom,100);});"
                + "mo.observe(document.body,{childList:true,subtree:true,characterData:true});"
                + "LG('MO active');"
                + "}catch(e){}"
                + "try{setInterval(fixDom,800);LG('dom watcher on');}catch(e){}"
                + "try{fixDom();}catch(e){}";
    }

    /**
     * v5.1 — ترقيع التخزين (نشط): localStorage + sessionStorage —
     * أي قيمة رقمية صغيرة أو "free" لمفتاح اشتراك/رصيد تُكتب مُرقّعة.
     */
    static String storageJs() {
        return "function fixStorage(st){try{"
                + "for(var i=0;i<st.length;i++){var k=st.key(i);if(!k)continue;"
                + "if(/credit|balance|quota|plan|member|sub/i.test(k)){"
                + "try{var v=st.getItem(k);"
                + "if(v&&/^[0-9]+$/.test(v)&&parseInt(v)<1000000){"
                + "st.setItem(k,'9999999');LG('storage+ '+k);}"
                + "else if(v==='free'){st.setItem(k,'plus');LG('storage+ '+k);}"
                + "}catch(e){}}}}catch(e){}}"
                + "try{setInterval(function(){fixStorage(localStorage);"
                + "fixStorage(sessionStorage);},2500);LG('storage watcher on');}catch(e){}"
                + "try{fixStorage(localStorage);fixStorage(sessionStorage);}catch(e){}";
    }

    /**
     * v5.0 — استطلاع localStorage (تشخيصي): يسجل أي مفتاح/قيمة تخص
     * الرصيد/الاشتراك — لنعرف مصدر الـ "0" بدقة في v5.1.
     */
    static String lsJs() {
        return "try{var _ls=window.localStorage;var _n=_ls.length;"
                + "for(var i=0;i<_n;i++){try{"
                + "var kk=_ls.key(i);var vv=_ls.getItem(kk);"
                + "var hay=String(kk)+'= '+String(vv).substring(0,120);"
                + "if(vv&&/credit|balance|quota|subscri|plan|premium/i.test(hay)){"
                + "LG('LS key: '+kk+'='+String(vv).substring(0,80));}"
                + "}catch(e){}}"
                + "LG('LS scan done n='+_n);}catch(e){LG('LS n/a');}";
    }

    static String lgPrelude() {
        return "var LG=(window.__ahL||function(m){try{console.log('[AppHook] '+m);}catch(e){}});";
    }

    /** chunk الأغلفة النهائية: fetch + XHR + bridge + إثبات */
    private static java.util.List<String> chunkFinals() {
        java.util.List<String> out = new java.util.ArrayList<>();
        // fetch
        out.add(chunkShell("F",
                "try{if(window.fetch){var _f=window.fetch;"
                + "window.fetch=function(){var a=arguments;"
                + "var u=(typeof a[0]==='string')?a[0]:((a[0]&&a[0].url)||'');"
                + "var sens=window.__ahR?window.__ahR.test(u):false;"
                + "if(sens){window.__ahL('fetch* '+u);}"
                + "try{return _f.apply(this,a).then(function(r){if(!sens){return r;}"
                + "try{return r.clone().text().then(function(t){"
                + "window.__ahL('ORIG['+u+']='+t.substring(0,700));"
                + "var q=window.__ahP(t);"
                + "if(q!==t){window.__ahL('PATCHED['+u+']');"
                + "var h=new Headers();"
                + "try{r.headers.forEach(function(v,k){"
                + "var lk=k.toLowerCase();"
                + "if(lk!=='content-length'&&lk!=='content-encoding')h.set(k,v);});}catch(e){}"
                + "return new Response(q,{status:r.status,statusText:r.statusText,headers:h});}"
                + "return r;}).catch(function(e){window.__ahL('readErr '+e);return r;});}"
                + "catch(e){window.__ahL('err '+e);return r;}});}"
                + "catch(e){return _f.apply(this,a);}};"
                + "window.__ahL('v5.1 fetch hooked');}else{window.__ahL('no fetch');}}catch(e){}",
                "fetch ready"));
        // XHR
        out.add(chunkShell("X",
                "try{if(window.XMLHttpRequest){var _X=window.XMLHttpRequest;"
                + "var _op=_X.prototype.open;var _sd=_X.prototype.send;"
                + "_X.prototype.open=function(m,u){try{this.__au=u;}catch(e){}return _op.apply(this,arguments);};"
                + "_X.prototype.send=function(){var self=this;"
                + "var sens=window.__ahR?window.__ahR.test(self.__au||''):false;"
                + "if(sens){try{self.addEventListener('readystatechange',function(){"
                + "if(self.readyState===4){try{"
                + "var o=self.responseText||'';"
                + "window.__ahL('XHR['+self.__au+'] s='+self.status+' len='+o.length);"
                + "var q=window.__ahP(o);"
                + "if(q!==o){window.__ahL('XPATCHED['+self.__au+']');"
                + "try{Object.defineProperty(self,'responseText',{value:q,configurable:true});}catch(e){}"
                + "try{Object.defineProperty(self,'response',{value:q,configurable:true});}catch(e){}}"
                + "}catch(e){}}});}catch(e){}}"
                + "return _sd.apply(this,arguments);};"
                + "window.__ahL('v5.1 xhr hooked');}else{window.__ahL('no xhr');}}catch(e){}",
                "xhr ready"));
        // bridge + إثبات (v49 flag + تمرير __ah دون مساس)
        out.add(chunkShell("B",
                "try{if(window.FlutterBridge&&window.FlutterBridge.postMessage&&!window.FlutterBridge.__v51){"
                + "window.FlutterBridge.__v51=true;"
                + "var _pm=window.FlutterBridge.postMessage;"
                + "window.FlutterBridge.postMessage=function(m){"
                + "try{if(typeof m==='string'&&(m.indexOf('__ah')>=0||m.indexOf('__apphook')>=0)){return _pm.apply(this,arguments);}}catch(e){}"
                + "try{if(typeof m==='string'&&m.length>1){var s=String(m);"
                + "window.__ahL('bridge-> '+s.substring(0,200));"
                + "var q=window.__ahP(s);"
                + "if(q!==s){window.__ahL('bridgePATCHED');}return _pm.call(this,q);}}catch(e){}"
                + "return _pm.apply(this,arguments);};window.__ahL('bridge hooked');}}catch(e){}"
                + "try{document.title='AH51_'+Date.now();}catch(e){}"
                + "try{document.body.style.backgroundColor='#00FF00';}catch(e){}",
                "bridge ready"));
        // v5.0: مراقب DOM + استطلاع localStorage (شرائح مستقلة)
        // v5.1: NUXT patcher (قبل DOM — state أولاً)
        out.add(chunkShell("N", lgPrelude() + nuxtJs(), "nuxt ready"));
        out.add(chunkShell("D", lgPrelude() + domJs(), "dom ready"));
        out.add(chunkShell("S", lgPrelude() + lsJs() + storageJs(), "ls ready"));
        return out;
    }

    static java.util.List<String> buildChunks() {
        java.util.List<String> out = new java.util.ArrayList<>();
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
        if (cur.length() > 0) {
            out.add(chunkRules(String.valueOf(idx++), cur.toString()));
        }
        out.addAll(chunkFinals());
        // ضمانة: لا chunk يتجاوز الحد
        for (String c : out) {
            if (c.length() > CHUNK_LIMIT) {
                try {
                    SafeHook.log(TAG + ": WARN chunk oversize len=" + c.length());
                } catch (Throwable ignored) {}
            }
        }
        return out;
    }

    /** الحقن الأساسي v4.9: شرائح loadUrl متسلسلة (300ms) + evaluate احتياطي */
    static void injectChunks(Object wv, String phase) {
        if (!(wv instanceof WebView)) return;
        final WebView view = (WebView) wv;
        String key;
        try {
            key = view.getClass().getName() + "@" + System.identityHashCode(view);
        } catch (Throwable ignored) {
            return;
        }
        if (!chunkedViews.add(key)) return; // مرة واحدة لكل WebView
        final java.util.List<String> chunks;
        try {
            chunks = patchChunks();
        } catch (Throwable t) {
            SafeHook.logError(TAG + ".chunks-build", t);
            return;
        }
        SafeHook.log(TAG + ": injecting " + chunks.size() + " parts (" + phase + ")");
        try {
            view.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        for (int i = 0; i < chunks.size(); i++) {
                            final String c = chunks.get(i);
                            try {
                                if (i == 0) {
                                    view.loadUrl(c);
                                } else {
                                    final int delay = i * 300;
                                    view.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            try {
                                                view.loadUrl(c);
                                            } catch (Throwable ignored) {}
                                        }
                                    }, delay);
                                }
                            } catch (Throwable ignored) {}
                        }
                        SafeHook.log(TAG + ": " + chunks.size()
                                + " parts queued (" + phase + ")");
                    } catch (Throwable t) {
                        SafeHook.logError(TAG + ".chunks-send", t);
                    }
                }
            });
        } catch (Throwable t) {
            SafeHook.logError(TAG + ".chunks-post", t);
        }
        if (chunkedViews.size() > 200) chunkedViews.clear();
    }

    private static final Set<String> chunkedViews =
            Collections.synchronizedSet(new HashSet<String>());

    static String buildPatchJs() {
        StringBuilder js = new StringBuilder(12000);
        js.append("(function(){");
        js.append("if(window.__apphook_v51){return;}window.__apphook_v51=true;");
        // v4.6: prefix "[AppHook] " ليطابق فلتر LegacyHook — هذا السطر يغير كل شيء
        js.append("function alog(m){");
        js.append("var s=String(m).substring(0,1200);");
        js.append("try{console.log('[AppHook] '+s);}catch(e){}");
        js.append("try{if(window.FlutterBridge&&window.FlutterBridge.postMessage){");
        js.append("window.FlutterBridge.postMessage(JSON.stringify({__ah:1,m:s}));}}catch(e){}}");
        js.append("try{alog('v5.1 loaded at '+location.href);}catch(e){}");
        // v4.9: قناة إثبات مستقلة عن console — تُقرأ عبر getTitle()
        // + وميض بصري (أخضر) يثبت التنفيذ بالعين المجردة
        js.append("try{document.title='AH51_'+Date.now();}catch(e){}");
        js.append("try{document.body.style.backgroundColor='#00FF00';}catch(e){}");
        // المسارات الحساسة
        js.append("var R=/\\/api\\/(user|payment\\/current_subscriptions|payment\\/get_credit_balance|quota\\/plan_usage|payment\\/sub2\\/tier_config|payment\\/sub2\\/pending_schedule|payment\\/add_tracking_event|review_reward)/i;");
        // دالة التعديل الشاملة
        js.append("function P(t){");
        js.append("if(typeof t!=='string'||t.length<2){return t;}");
        js.append("var p=t;");
        // القواعد من JS_RULES (المصدر الوحيد — تُستخدم أيضاً للشرائح)
        for (String[] rl : JS_RULES) addRule(js, rl[0], rl[1]);
        js.append("return p;}");
        js.append("function isApi(u){return typeof u==='string'&&R.test(u);}");
        js.append("function wrapText(u,t){alog('ORIG['+u+']='+t.substring(0,1200));var p;try{p=P(t);}catch(e){return t;}if(p!==t){alog('PATCHED['+u+']');}return p;}");
        // fetch
        js.append("try{if(window.fetch){var _f=window.fetch;");
        js.append("window.fetch=function(){var a=arguments;var u=(typeof a[0]==='string')?a[0]:((a[0]&&a[0].url)||'');var sens=isApi(u);");
        js.append("if(sens){alog('fetch* '+u);}");
        js.append("try{return _f.apply(this,a).then(function(r){if(!sens){return r;}");
        js.append("try{return r.clone().text().then(function(t){var q=wrapText(u,t);if(q!==t){"
                + "var h=new Headers();"
                + "try{r.headers.forEach(function(v,k){var lk=k.toLowerCase();"
                + "if(lk!=='content-length'&&lk!=='content-encoding')h.set(k,v);});}catch(e){}"
                + "return new Response(q,{status:r.status,statusText:r.statusText,headers:h});}return r;})");
        js.append(".catch(function(e){alog('readErr '+e);return r;});}catch(e){alog('err '+e);return r;}});}");
        js.append("catch(e){return _f.apply(this,a);}};alog('fetch patched');}else{alog('no fetch');}}catch(e){alog('fetchErr '+e);}");
        // XHR (readystatechange — تغطية أوسع)
        js.append("try{if(window.XMLHttpRequest){var _X=window.XMLHttpRequest;var _op=_X.prototype.open;var _sd=_X.prototype.send;");
        js.append("_X.prototype.open=function(m,u){try{this.__au=u;}catch(e){}return _op.apply(this,arguments);};");
        js.append("_X.prototype.send=function(){var self=this;var sens=isApi(self.__au||'');");
        js.append("if(sens){try{self.addEventListener('readystatechange',function(){if(self.readyState===4){try{");
        js.append("var orig=self.responseText||'';alog('XHR['+self.__au+'] s='+self.status+' len='+orig.length);");
        js.append("var q=P(orig);if(q!==orig){alog('XPATCHED['+self.__au+']');");
        js.append("try{Object.defineProperty(self,'responseText',{value:q,configurable:true});}catch(e){}");
        js.append("try{Object.defineProperty(self,'response',{value:q,configurable:true});}catch(e){}}");
        js.append("}catch(e){}});}catch(e){}}return _sd.apply(this,arguments);};alog('xhr patched');}else{alog('no xhr');}}catch(e){alog('xhrErr '+e);}");
        // callHandler (Promise) — تمرير رسائل __apphook دون مساس غير مطبق هنا (لا يستخدمها)
        js.append("try{if(window.flutter_inappwebview&&window.flutter_inappwebview.callHandler){");
        js.append("var _c=window.flutter_inappwebview.callHandler;");
        js.append("window.flutter_inappwebview.callHandler=function(){var a=arguments;");
        js.append("try{var r=_c.apply(this,a);if(r&&r.then){return r.then(function(v){");
        js.append("try{if(typeof v==='string'&&v.length>1){var q=P(v);if(q!==v){alog('PATCHED[bridge]');}return q;}}catch(e){}return v;});}");
        js.append("return r;}catch(e){return _c.apply(this,a);}};alog('bridge-call patched');}}catch(e){}");
        // FlutterBridge.postMessage — hookBridge مع retry (الجسر قد يُعرّف متأخراً)
        // + تمرير رسائل __ah/__apphook دون مساس (حرج لـ alog)
        js.append("function hookBridge(){");
        js.append("if(!window.FlutterBridge||window.FlutterBridge.__v51){return false;}");
        js.append("try{window.FlutterBridge.__v51=true;");
        js.append("var _pm=window.FlutterBridge.postMessage;");
        js.append("window.FlutterBridge.postMessage=function(m){");
        js.append("try{if(typeof m==='string'&&(m.indexOf('__ah')>=0||m.indexOf('__apphook')>=0)){return _pm.apply(this,arguments);}}catch(e){}");
        js.append("try{if(typeof m==='string'&&m.length>1){var s=String(m);alog('bridge-> '+s.substring(0,400));var q=P(s);");
        js.append("if(q!==s){alog('bridgePATCHED');}return _pm.call(this,q);}}catch(e){}");
        js.append("return _pm.apply(this,arguments);};alog('bridge hooked');return true;}catch(e){return false;}}");
        js.append("hookBridge();var _n=0;var _iv=setInterval(function(){_n++;if(hookBridge()||_n>100){clearInterval(_iv);}},100);");
        // v5.0: مراقب DOM + استطلاع LS في السكربت الكامل أيضاً
        js.append(lgPrelude());
        js.append(nuxtJs());
        js.append(domJs());
        js.append(lsJs());
        js.append(storageJs());
        js.append("})();");
        return js.toString();
    }

    // ── 2) قناة logging: اعتراض postMessage الأصلي ─────────────────

    /** يستخرج حقل m من رسالة {"__ah":1,"m":"..."} (أو __apphook للتوافق) */
    static String extractLogBody(String msg) {
        if (msg == null || (msg.indexOf("__ah") < 0 && msg.indexOf("__apphook") < 0)) {
            return null;
        }
        try {
            org.json.JSONObject o = new org.json.JSONObject(msg);
            String m = o.optString("m", null);
            if (m != null && m.length() > 0) return m;
        } catch (Throwable ignored) {}
        try {
            int i = msg.indexOf("\"m\":\"");
            if (i >= 0) {
                int start = i + 5;
                int end = msg.lastIndexOf("\"}");
                if (end < 0) end = msg.lastIndexOf("\"");
                if (end > start) {
                    return msg.substring(start, end)
                            .replace("\\\"", "\"")
                            .replace("\\n", "\n")
                            .replace("\\\\", "\\");
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static void hookBridgeOn(XposedInterface fw, Class<?> clazz) {
        if (clazz == null || !hookedClasses.add("BR:" + clazz.getName())) return;
        FlutterChannelHook.hookAll(fw, clazz, "postMessage", 1, 3,
                "WebView.bridgeLog", chain -> {
                    try {
                        for (Object a : chain.getArgs()) {
                            if (!(a instanceof String)) continue;
                            String body = extractLogBody((String) a);
                            if (body != null) {
                                SafeHook.log("[WebView-JS] " + body);
                                if (body.contains("PATCHED[") || body.contains("XPATCHED[")) {
                                    SafeHook.countFeature("webview-patch");
                                }
                                break;
                            }
                        }
                    } catch (Throwable t) {
                        SafeHook.logError(TAG + ".bridgeLog", t);
                    }
                    return chain.proceed(); // القناة تمر كما هي — قراءة فقط
                });
    }

    private static void hookBridgeLog(XposedInterface fw, ClassLoader cl, ApplicationInfo ai) {
        for (String name : KNOWN_BRIDGES) {
            Class<?> c = FingerprintEngine.findClassQuiet(cl, name);
            if (c == null) continue;
            hookBridgeOn(fw, c);
            SafeHook.log(TAG + ": known bridge hooked: " + name);
        }
        if (ai == null) return;
        List<String> paths = new ArrayList<>();
        if (ai.sourceDir != null) paths.add(ai.sourceDir);
        try {
            if (ai.splitSourceDirs != null) {
                for (String s : ai.splitSourceDirs) {
                    if (s != null) paths.add(s);
                }
            }
        } catch (Throwable ignored) {}
        int n = 0;
        for (String path : paths) {
            DexFile dex = null;
            try {
                dex = new DexFile(path);
                Enumeration<String> it = dex.entries();
                while (it.hasMoreElements()) {
                    String name;
                    try {
                        name = it.nextElement();
                    } catch (Throwable ignored) {
                        continue;
                    }
                    if (name == null) continue;
                    String l = name.toLowerCase();
                    boolean hit = (l.contains("bridge") && (l.contains("javascript")
                            || l.contains("interface") || l.contains("inappwebview")))
                            || l.contains("javascriptbridge")
                            || l.contains("javascriptinterface");
                    if (!hit) continue;
                    if (n >= 20) break;
                    Class<?> c = FingerprintEngine.findClassQuiet(cl, name);
                    if (c == null) continue;
                    boolean hasPm = false;
                    try {
                        for (Method m : c.getDeclaredMethods()) {
                            if (m.getName().equals("postMessage")) {
                                hasPm = true;
                                break;
                            }
                        }
                    } catch (Throwable ignored) {}
                    if (!hasPm) continue;
                    hookBridgeOn(fw, c);
                    n++;
                }
            } catch (Throwable ignored) {
            } finally {
                if (dex != null) {
                    try {
                        dex.close();
                    } catch (Throwable ignored) {}
                }
            }
        }
        SafeHook.log(TAG + ": bridge scan done (" + n + " hooked)");
    }

    // ── 3) اختبار الإثبات البصري v4.8 ─────────────────────────────

    /**
     * سكربت اختبار صغير عبر loadUrl (قناة بديلة عن evaluateJavascript).
     * أخضر = JS يُنفَّذ حتماً. أسود = الحقن نفسه مكسور.
     */
    static final String TEST_JS =
            "javascript:(function(){"
            + "try{document.body.style.backgroundColor='#00FF00';}catch(e){}"
                + "try{document.title='AH51_'+Date.now();}catch(e){}"
                + "try{console.log('[AppHook] TEST_JS_EXECUTED');}catch(e){}"
            + "})();";

    static void sendTestJs(Object wv, String why) {
        if (!(wv instanceof WebView)) return;
        final WebView view = (WebView) wv;
        try {
            // onPageStarted/Finished على خيط UI — loadUrl مباشر آمن
            view.loadUrl(TEST_JS);
            SafeHook.log(TAG + ": TEST_JS sent (" + why + ")");
        } catch (Throwable t) {
            SafeHook.logError(TAG + ".testJs", t);
        }
    }

    // ── 4) الحقن (خيط UI دائماً) ──────────────────────────────────

    static void injectJs(Object wv, final String phase) {
        if (!(wv instanceof WebView)) return;
        final WebView view = (WebView) wv;
        String key;
        try {
            key = view.getClass().getName() + "@" + System.identityHashCode(view);
        } catch (Throwable ignored) {
            return;
        }
        final boolean first = injectedViews.add(key);
        try {
            view.post(new Runnable() {
                @Override
                public void run() {
                    try {
                        // v4.8: callback يُسجل قيمة الإرجاع — null = خطأ صياغة محتمل
                        view.evaluateJavascript(patchJs(),
                                new android.webkit.ValueCallback<String>() {
                                    @Override
                                    public void onReceiveValue(String value) {
                                        try {
                                            SafeHook.log(TAG + ": evalCallback(" + phase
                                                    + ") = " + cap(String.valueOf(value)));
                                        } catch (Throwable ignored) {}
                                    }
                                });
                        if (first) SafeHook.log(TAG + ": JS injected (" + phase + ")");
                    } catch (Throwable t) {
                        SafeHook.logError(TAG + ".inject", t);
                    }
                }
            });
        } catch (Throwable t) {
            SafeHook.logError(TAG + ".post", t);
        }
        if (injectedViews.size() > 200) injectedViews.clear();
    }

    // ── 5) الربط ──────────────────────────────────────────────────

    private static Class<?> sysClass(String name) {
        try {
            return Class.forName(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean httpUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private static void hookJsBridge(XposedInterface fw) {
        Class<?> wv = sysClass("android.webkit.WebView");
        if (wv == null) {
            SafeHook.logSafe(TAG + ".addJavascriptInterface", "WebView absent");
            return;
        }
        Method m = FingerprintEngine.findMethodByFingerprint(wv,
                "addJavascriptInterface", void.class, 2);
        if (m == null) {
            SafeHook.logSafe(TAG + ".addJavascriptInterface", "method-not-found");
            return;
        }
        final Method target = m;
        try {
            fw.hook(target)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        try {
                            Object name = chain.getArg(1);
                            SafeHook.log(TAG + ": bridge reg: " + name);
                            Object obj = chain.getArg(0);
                            if (obj != null) {
                                hookBridgeOn(fw, obj.getClass()); // الجسر الفعلي — قناة log
                                SafeHook.log(TAG + "   iface = " + obj.getClass().getName());
                            }
                        } catch (Throwable t) {
                            SafeHook.logError(TAG + ".jsBridge", t);
                        }
                        return chain.proceed();
                    });
            SafeHook.log("Hook OK: WebView.addJavascriptInterface -> " + m);
        } catch (Throwable t) {
            SafeHook.logError(TAG + ".jsBridge", t);
        }
    }

    /** مراقبة فقط — لا حظر أبداً + حقن مبكر + ربط فئة العميل الديناميكية */
    private static void hookShouldIntercept(XposedInterface fw, ClassLoader cl) {
        Class<?> client = sysClass("android.webkit.WebViewClient");
        if (client == null) {
            client = FingerprintEngine.findClassQuiet(cl, "android.webkit.WebViewClient");
        }
        if (client == null) {
            SafeHook.logSafe(TAG + ".shouldInterceptRequest", "WebViewClient absent");
            return;
        }
        try {
            boolean any = false;
            for (Method m : client.getDeclaredMethods()) {
                if (!m.getName().equals("shouldInterceptRequest")) continue;
                if (m.getParameterTypes().length != 2) continue;
                try {
                    m.setAccessible(true);
                    final Method target = m;
                    fw.hook(target)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                try {
                                    hookClientClass(fw, chain.getThisObject().getClass());
                                    Object wv = chain.getArg(0);
                                    if (wv != null) injectJs(wv, "intercept");
                                    String url = extractUrl(chain.getArg(1));
                                    if (url != null && isPayUrl(url)) {
                                        SafeHook.log(TAG + ": WebView REQ: " + url);
                                        SafeHook.logNetwork("WebView", url, -1, -1);
                                    }
                                } catch (Throwable t) {
                                    SafeHook.logError(TAG + ".intercept", t);
                                }
                                return chain.proceed();
                            });
                    SafeHook.log("Hook OK: WebViewClient.shouldInterceptRequest -> " + m);
                    any = true;
                } catch (Throwable t) {
                    SafeHook.logError(TAG + ".intercept", t);
                }
            }
            if (!any) SafeHook.logSafe(TAG + ".shouldInterceptRequest", "no 2-arg overload");
        } catch (Throwable t) {
            SafeHook.logError(TAG + ".intercept-scan", t);
        }
    }

    private static void hookPageStarted(XposedInterface fw) {
        Class<?> client = sysClass("android.webkit.WebViewClient");
        if (client == null) {
            SafeHook.logSafe(TAG + ".onPageStarted", "WebViewClient absent");
            return;
        }
        FlutterChannelHook.hookAll(fw, client, "onPageStarted", 3, 3,
                "WebView.onPageStarted", chain -> {
                    try {
                        Object wv = chain.getArg(0);
                        Object u = chain.getArg(1);
                        if (wv != null && httpUrl((String) u)) {
                            injectJs(wv, "start"); // evaluate (احتياطي)
                            injectChunks(wv, "start"); // loadUrl (أساسي v4.9)
                            if (isGenspark((String) u)) sendTestJs(wv, "started");
                        }
                    } catch (Throwable t) {
                        SafeHook.logError(TAG + ".started", t);
                    }
                    return chain.proceed();
                });
    }

    private static void hookPageFinished(XposedInterface fw) {
        Class<?> client = sysClass("android.webkit.WebViewClient");
        if (client == null) {
            SafeHook.logSafe(TAG + ".onPageFinished", "WebViewClient absent");
            return;
        }
        hookFinishedOn(fw, client);
    }

    private static void hookFinishedOn(XposedInterface fw, Class<?> clazz) {
        if (clazz == null || !hookedClasses.add("FIN:" + clazz.getName())) return;
        FlutterChannelHook.hookAll(fw, clazz, "onPageFinished", 2, 2,
                "WebView.onPageFinished", chain -> {
                    try {
                        final Object wv = chain.getArg(0);
                        Object url = chain.getArg(1);
                        if (wv != null && httpUrl((String) url)) {
                            String key = "f:" + url;
                            if (injectedPages.add(key)) {
                                SafeHook.log(TAG + ": page finished: " + url);
                            }
                            injectJs(wv, "finish"); // evaluate (احتياطي)
                            injectChunks(wv, "finish"); // loadUrl (أساسي v4.9)
                            // v4.8: اختبار بديل متأخر عبر loadUrl (قناة مختلفة)
                            if (isGenspark((String) url) && wv instanceof WebView) {
                                final WebView view = (WebView) wv;
                                try {
                                    view.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            sendTestJs(view, "delayed");
                                        }
                                    }, 2000);
                                } catch (Throwable ignored) {}
                            }
                        }
                        if (injectedPages.size() > 200) injectedPages.clear();
                    } catch (Throwable t) {
                        SafeHook.logError(TAG + ".finished", t);
                    }
                    return chain.proceed();
                });
    }

    private static boolean isGenspark(String url) {
        return url != null && url.contains("genspark.ai");
    }

    /** ربط فئة عميل concrete (override لـ onPageFinished) عند أول ظهور */
    static void hookClientClass(XposedInterface fw, Class<?> clazz) {
        if (clazz == null) return;
        if (clazz == WebViewClient.class) return;
        hookFinishedOn(fw, clazz);
    }

    /** onConsoleMessage — القناة الأساسية في v4.5 (أثبتت عملها في v4.2) */
    private static void hookConsole(XposedInterface fw) {
        Class<?> chrome = sysClass("android.webkit.WebChromeClient");
        if (chrome == null) {
            SafeHook.logSafe(TAG + ".onConsoleMessage", "WebChromeClient absent");
            return;
        }
        hookConsoleOn(fw, chrome);
    }

    /**
     * v4.7 — الإصلاح الحاسم: اعتراض setWebChromeClient لالتقاط الفئة
     * الفعلية (InAppWebViewChromeClient) وربط onConsoleMessage عليها.
     * ربط الـ base وحده يفشل لأن الـ subclass لا يستدعي super.
     */
    private static void hookSetChromeClient(XposedInterface fw) {
        Class<?> wv = sysClass("android.webkit.WebView");
        Class<?> wcc = sysClass("android.webkit.WebChromeClient");
        if (wv == null || wcc == null) {
            SafeHook.logSafe(TAG + ".setWebChromeClient", "WebView/ChromeClient absent");
            return;
        }
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
                                Class<?> actual = client.getClass();
                                SafeHook.log(TAG + ": Real ChromeClient = "
                                        + actual.getName());
                                hookActualChromeClass(fw, actual);
                            }
                        } catch (Throwable t) {
                            SafeHook.logError(TAG + ".setWebChromeClient", t);
                        }
                        return chain.proceed(); // التسجيل يتم طبيعياً
                    });
            SafeHook.log("Hook OK: WebView.setWebChromeClient -> " + m);
        } catch (Throwable t) {
            SafeHook.logError(TAG + ".setWebChromeClient", t);
        }
    }

    /** ربط onConsoleMessage على الفئة الفعلية (مرة واحدة لكل فئة) */
    static void hookActualChromeClass(XposedInterface fw, Class<?> actual) {
        if (actual == null) return;
        if (!hookedClasses.add("REAL:" + actual.getName())) return;
        FlutterChannelHook.hookAll(fw, actual, "onConsoleMessage", 1, 3,
                "WebView.realConsole", chain -> {
                    try {
                        String text = extractConsole(chain);
                        if (text != null && (text.contains("[AppHook]")
                                || text.contains("PATCHED[") || text.contains("ORIG["))) {
                            SafeHook.log("[WebView-JS] " + cap(text));
                            if (text.contains("PATCHED[")) {
                                SafeHook.countFeature("webview-patch");
                            }
                        }
                    } catch (Throwable t) {
                        SafeHook.logError(TAG + ".realConsole", t);
                    }
                    return chain.proceed();
                });
        try {
            SafeHook.log(TAG + ": hooked " + actual.getSimpleName());
        } catch (Throwable ignored) {}
    }

    /**
     * v4.7 — قناة إثبات مستقلة: JS يضع AH47_... في document.title،
     * وهنا نرصده. يعمل حتى لو كانت كل قنوات الـ log محجوبة.
     */
    private static void hookGetTitle(XposedInterface fw) {
        Class<?> wv = sysClass("android.webkit.WebView");
        if (wv == null) {
            SafeHook.logSafe(TAG + ".getTitle", "WebView absent");
            return;
        }
        Method m = FingerprintEngine.findMethodByFingerprint(wv, "getTitle",
                String.class, 0);
        if (m == null) {
            SafeHook.logSafe(TAG + ".getTitle", "method-not-found");
            return;
        }
        final Method target = m;
        try {
            fw.hook(target)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        try {
                            Object orig = chain.proceed();
                            if (orig instanceof String
                                    && ((String) orig).startsWith("AH51_")) {
                                SafeHook.log(TAG + ": ** JS PROOF — " + orig);
                                SafeHook.countFeature("webview-js-exec");
                            }
                            return orig;
                        } catch (Throwable t) {
                            SafeHook.logError(TAG + ".getTitle", t);
                            return chain.proceed();
                        }
                    });
            SafeHook.log("Hook OK: WebView.getTitle -> " + m);
        } catch (Throwable t) {
            SafeHook.logError(TAG + ".getTitle", t);
        }
    }

    static void hookConsoleOn(XposedInterface fw, Class<?> clazz) {
        if (clazz == null || !hookedClasses.add("CON:" + clazz.getName())) return;
        FlutterChannelHook.hookAll(fw, clazz, "onConsoleMessage", 1, 3,
                "WebView.onConsoleMessage", chain -> {
                    try {
                        String text = extractConsole(chain);
                        if (text != null && (text.contains("[AppHook]")
                                || text.contains("PATCHED[") || text.contains("ORIG["))) {
                            SafeHook.log("[WebView-JS] " + cap(text));
                            if (text.contains("PATCHED[")) {
                                SafeHook.countFeature("webview-patch");
                            }
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

    private static String cap(String s) {
        if (s == null) return "null";
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    private static void hookKnownClients(XposedInterface fw, ClassLoader cl) {
        for (String name : KNOWN_CLIENTS) {
            Class<?> c = FingerprintEngine.findClassQuiet(cl, name);
            if (c == null) continue;
            hookFinishedOn(fw, c);
            hookConsoleOn(fw, c);
            SafeHook.log(TAG + ": known client hooked: " + name);
        }
    }

    /** مسح Dex عن فئات *WebViewClient/*ChromeClient (جزئي — ليست أسماء ثابتة) */
    private static void hookDiscoveredClients(XposedInterface fw, ApplicationInfo ai) {
        if (ai == null) return;
        List<String> paths = new ArrayList<>();
        if (ai.sourceDir != null) paths.add(ai.sourceDir);
        try {
            if (ai.splitSourceDirs != null) {
                for (String s : ai.splitSourceDirs) {
                    if (s != null) paths.add(s);
                }
            }
        } catch (Throwable ignored) {}
        int n = 0;
        for (String path : paths) {
            DexFile dex = null;
            try {
                dex = new DexFile(path);
                Enumeration<String> it = dex.entries();
                while (it.hasMoreElements()) {
                    String name;
                    try {
                        name = it.nextElement();
                    } catch (Throwable ignored) {
                        continue;
                    }
                    if (name == null) continue;
                    String l = name.toLowerCase();
                    if (!(l.contains("webviewclient") || l.contains("chromeclient"))) continue;
                    if (l.startsWith("android.webkit.")) continue;
                    if (n >= 30) break;
                    n++;
                }
            } catch (Throwable ignored) {
            } finally {
                if (dex != null) {
                    try {
                        dex.close();
                    } catch (Throwable ignored) {}
                }
            }
        }
        SafeHook.log(TAG + ": client-class scan noted (" + n + " candidate(s)) — dynamic hook active");
    }

    private static String extractUrl(Object req) {
        if (req == null) return null;
        if (req instanceof String) return (String) req;
        try {
            Object uri = ReflectCall.call(req, "getUrl");
            if (uri != null) return String.valueOf(uri);
        } catch (Throwable ignored) {}
        return null;
    }

    static boolean isPayUrl(String url) {
        if (url == null) return false;
        String l = url.toLowerCase();
        return l.contains("subscri") || l.contains("premium") || l.contains("billing")
                || l.contains("pay") || l.contains("checkout") || l.contains("plan")
                || l.contains("upgrade") || l.contains("purchase");
    }

    /** فحص داخلي: سلامة السكربت + تطابق القواعد + عينة balance=8 */
    public static boolean selfTest() {
        try {
            String js = patchJs();
            if (js == null || js.length() < 4000) return false;
            if (!js.contains("__apphook_v51") || !js.contains("__ah")) return false;
            if (!js.contains("9999999") || !js.contains("vm_quota_team")) return false;
            if (!js.contains("console.log('[AppHook] '")) return false; // prefix يطابق الفلتر
            if (!js.contains("AH51_") || !js.contains("00FF00")) return false; // الإثبات البصري
            if (!js.contains("patchNuxt") || !js.contains("fixStorage")) return false; // v5.1
            if (!patchChunks().isEmpty() && patchChunks().size() < 3) return false;
            for (String c : patchChunks()) {
                if (c.length() > CHUNK_LIMIT) return false; // حد loadUrl
            }
            if (TEST_JS == null || !TEST_JS.startsWith("javascript:")
                    || !TEST_JS.contains("TEST_JS_EXECUTED")) return false;
            String sample = "{\"data\":{\"balance\":8}}";
            String out = SubscriptionPatch.patchJson(sample);
            return out.contains("9999999");
        } catch (Throwable ignored) {
            return false;
        }
    }
}
