package com.apphook.log.hooks;

import java.util.concurrent.atomic.AtomicInteger;
import com.apphook.log.Config;
import com.apphook.log.SafeHook;

/**
 * BalanceFreeze v6.2
 *
 * يعمل على ثلاث طبقات:
 *  - Fetch/Response: تجميد قيمة balance في الاستجابات
 *  - Request Body: تحويل النموذج المدفوع إلى مجاني
 *  - Events: حجب add_tracking_event الذي يستهلك الرصيد
 */
public final class BalanceFreezeHook {
    private BalanceFreezeHook() {}

    private static final String TAG = "BalanceFreeze";

    // أول قيمة مقروءة من السيرفر (لتثبيتها)
    private static final AtomicInteger frozenValue = new AtomicInteger(-1);
    private static volatile boolean initialized = false;

    public static int getFrozenValue() {
        return frozenValue.get() > 0 ? frozenValue.get() : Config.FROZEN_BALANCE;
    }

    /** تثبيت أول قيمة مقروءة (تُستدعى عند رصد balance حقيقي). */
    public static void initFrozen(int v) {
        if (!initialized && v > 0) {
            frozenValue.set(v);
            initialized = true;
            SafeHook.log(TAG + ": frozen at " + v);
        }
    }

    /** تجميد JSON استجابة (مرآة JS — نفس القواعد). */
    public static String freezeResponseJson(String t) {
        if (t == null || t.length() < 5) return t;
        String p = t;
        try {
            p = p.replaceAll("\"balance\"\\s*:\\s*-?[0-9]+", "\"balance\":" + getFrozenValue());
            p = p.replaceAll("\"credit_balance\"\\s*:\\s*-?[0-9]+", "\"credit_balance\":" + getFrozenValue());
            p = p.replaceAll("\"creditBalance\"\\s*:\\s*-?[0-9]+", "\"creditBalance\":" + getFrozenValue());
            p = p.replaceAll("\"credits\"\\s*:\\s*-?[0-9]+(?=[,}])", "\"credits\":" + getFrozenValue());
            p = p.replaceAll("\"credit\"\\s*:\\s*-?[0-9]+", "\"credit\":" + getFrozenValue());
            p = p.replaceAll("\"remaining\"\\s*:\\s*-?[0-9]+", "\"remaining\":" + getFrozenValue());
            p = p.replaceAll("\"remaining_balance\"\\s*:\\s*-?[0-9]+", "\"remaining_balance\":" + getFrozenValue());
            p = p.replaceAll("\"remaining_credits\"\\s*:\\s*-?[0-9]+", "\"remaining_credits\":" + getFrozenValue());
            p = p.replaceAll("\"available\"\\s*:\\s*-?[0-9]+", "\"available\":" + getFrozenValue());
            p = p.replaceAll("\"available_credits\"\\s*:\\s*-?[0-9]+", "\"available_credits\":" + getFrozenValue());
            p = p.replaceAll("\"consumed\"\\s*:[0-9]+", "\"consumed\":0");
            p = p.replaceAll("\"used\"\\s*:[0-9]+", "\"used\":0");
            p = p.replaceAll("\"used_credits\"\\s*:[0-9]+", "\"used_credits\":0");
            p = p.replaceAll("\"spent\"\\s*:[0-9]+", "\"spent\":0");
            p = p.replaceAll("\"quota_used\"\\s*:[0-9]+", "\"quota_used\":0");
            p = p.replaceAll("\"quota_consumed\"\\s*:[0-9]+", "\"quota_consumed\":0");
        } catch (Throwable ignored) {
            return t;
        }
        return p;
    }

    /** تجميد Request Body (مرآة JS — نفس القواعد). */
    public static String freezeRequestBody(String body) {
        if (body == null || body.length() < 5) return body;
        String p = body;
        try {
            p = p.replaceAll("\"model\"\\s*:\\s*\"super_agent\"", "\"model\":\"" + Config.FREE_MODEL_NAME + "\"");
            p = p.replaceAll("\"model_name\"\\s*:\\s*\"super_agent\"", "\"model_name\":\"" + Config.FREE_MODEL_NAME + "\"");
            p = p.replaceAll("\"agent\"\\s*:\\s*\"super_agent\"", "\"agent\":\"chat\"");
            p = p.replaceAll("\"agent_type\"\\s*:\\s*\"super_agent\"", "\"agent_type\":\"chat\"");
            p = p.replaceAll("\"use_credits\"\\s*:\\s*true", "\"use_credits\":false");
            p = p.replaceAll("\"deduct_credits\"\\s*:\\s*true", "\"deduct_credits\":false");
            p = p.replaceAll("\"charge_credits\"\\s*:\\s*true", "\"charge_credits\":false");
            p = p.replaceAll("\"consume_credits\"\\s*:\\s*true", "\"consume_credits\":false");
            p = p.replaceAll("\"paid_only\"\\s*:\\s*true", "\"paid_only\":false");
            p = p.replaceAll("\"requires_credits\"\\s*:\\s*true", "\"requires_credits\":false");
            p = p.replaceAll("\"requires_balance\"\\s*:\\s*true", "\"requires_balance\":false");
            p = p.replaceAll("\"model\"\\s*:\\s*\"gpt-4\"", "\"model\":\"" + Config.FREE_MODEL_NAME + "\"");
            p = p.replaceAll("\"model\"\\s*:\\s*\"gpt-4-turbo\"", "\"model\":\"" + Config.FREE_MODEL_NAME + "\"");
            p = p.replaceAll("\"model\"\\s*:\\s*\"claude-3-opus\"", "\"model\":\"" + Config.FREE_MODEL_NAME + "\"");
        } catch (Throwable ignored) {
            return body;
        }
        return p;
    }

    /**
     * JS يُحقن في WebView: ثلاث وظائف في سكربت واحد
     */
    public static final String JS_FREEZE =
        "(function(){" +
        "if(window.__freeze)return;window.__freeze=1;" +
        "function L(m){try{console.log('[FREEZE] '+String(m).substring(0,400));}catch(e){}}" +
        "L('balance-freeze installed');" +

        // ─── 1. تجميد الاستجابات: أي حقل balance/credit يعود لقيمة ثابتة
        "window.__freezeResponse=function(t){" +
        "  if(typeof t!=='string'||t.length<5)return t;" +
        "  var p=t;" +
        // balance/credit_balance/credits → 100
        "  p=p.replace(/\"balance\":\\s*-?[0-9]+/g,'\"balance\":9999999');" +
        "  p=p.replace(/\"credit_balance\":\\s*-?[0-9]+/g,'\"credit_balance\":9999999');" +
        "  p=p.replace(/\"creditBalance\":\\s*-?[0-9]+/g,'\"creditBalance\":9999999');" +
        "  p=p.replace(/\"credits\":\\s*-?[0-9]+(?=[,}])/g,'\"credits\":9999999');" +
        "  p=p.replace(/\"credit\":\\s*-?[0-9]+/g,'\"credit\":9999999');" +
        "  p=p.replace(/\"remaining\":\\s*-?[0-9]+/g,'\"remaining\":9999999');" +
        "  p=p.replace(/\"remaining_balance\":\\s*-?[0-9]+/g,'\"remaining_balance\":9999999');" +
        "  p=p.replace(/\"remaining_credits\":\\s*-?[0-9]+/g,'\"remaining_credits\":9999999');" +
        "  p=p.replace(/\"available\":\\s*-?[0-9]+/g,'\"available\":9999999');" +
        "  p=p.replace(/\"available_credits\":\\s*-?[0-9]+/g,'\"available_credits\":9999999');" +
        "  p=p.replace(/\"plan\":\\s*\"free\"/g,'\"plan\":\"plus\"');" +
        "  p=p.replace(/\"tier\":\\s*\"free\"/g,'\"tier\":\"plus\"');" +
        "  p=p.replace(/\"subType\":\\s*\"free\"/g,'\"subType\":\"plus\"');" +
        "  p=p.replace(/\"membershipType\":\\s*\"free\"/g,'\"membershipType\":\"plus\"');" +
        // consumed → 0
        "  p=p.replace(/\"consumed\":\\s*[0-9]+/g,'\"consumed\":0');" +
        "  p=p.replace(/\"used\":\\s*[0-9]+/g,'\"used\":0');" +
        "  p=p.replace(/\"used_credits\":\\s*[0-9]+/g,'\"used_credits\":0');" +
        "  p=p.replace(/\"spent\":\\s*[0-9]+/g,'\"spent\":0');" +
        // quota
        "  p=p.replace(/\"quota_used\":\\s*[0-9]+/g,'\"quota_used\":0');" +
        "  p=p.replace(/\"quota_consumed\":\\s*[0-9]+/g,'\"quota_consumed\":0');" +
        "  return p;" +
        "};" +

        // ─── 2. تعديل الطلبات: تحويل النموذج المدفوع إلى مجاني
        "window.__freezeRequest=function(body){" +
        "  if(typeof body!=='string'||body.length<5)return body;" +
        "  var p=body;" +
        // تغيير النموذج المدفوع
        "  p=p.replace(/\"model\":\\s*\"super_agent\"/gi,'\"model\":\"gpt-4o-mini\"');" +
        "  p=p.replace(/\"model_name\":\\s*\"super_agent\"/gi,'\"model_name\":\"gpt-4o-mini\"');" +
        "  p=p.replace(/\"agent\":\\s*\"super_agent\"/gi,'\"agent\":\"chat\"');" +
        "  p=p.replace(/\"agent_type\":\\s*\"super_agent\"/gi,'\"agent_type\":\"chat\"');" +
        // منع خصم الرصيد
        "  p=p.replace(/\"use_credits\":\\s*true/gi,'\"use_credits\":false');" +
        "  p=p.replace(/\"deduct_credits\":\\s*true/gi,'\"deduct_credits\":false');" +
        "  p=p.replace(/\"charge_credits\":\\s*true/gi,'\"charge_credits\":false');" +
        "  p=p.replace(/\"consume_credits\":\\s*true/gi,'\"consume_credits\":false');" +
        "  p=p.replace(/\"paid_only\":\\s*true/gi,'\"paid_only\":false');" +
        "  p=p.replace(/\"requires_credits\":\\s*true/gi,'\"requires_credits\":false');" +
        "  p=p.replace(/\"requires_balance\":\\s*true/gi,'\"requires_balance\":false');" +
        // تغيير النموذج الافتراضي
        "  p=p.replace(/\"model\":\\s*\"gpt-4\"/gi,'\"model\":\"gpt-4o-mini\"');" +
        "  p=p.replace(/\"model\":\\s*\"gpt-4-turbo\"/gi,'\"model\":\"gpt-4o-mini\"');" +
        "  p=p.replace(/\"model\":\\s*\"claude-3-opus\"/gi,'\"model\":\"gpt-4o-mini\"');" +
        "  return p;" +
        "};" +

        // ─── 3. اعتراض fetch (request + response)
        "var _f=window.fetch;" +
        "if(_f){" +
        "  window.fetch=function(url,opts){" +
        "    try{" +
        "      var u=(typeof url==='string')?url:(url&&url.url)||'';" +
        "      var isApi=/\\/api\\//i.test(u);" +
        "      if(isApi&&opts&&opts.body&&typeof opts.body==='string'){" +
        "        var orig=opts.body;" +
        "        var patched=window.__freezeRequest(orig);" +
        "        if(patched!==orig){" +
        "          L('REQ-PATCHED: '+u);" +
        "          opts.body=patched;" +
        "        }" +
        "      }" +
        "    }catch(e){}" +
        "    return _f.apply(this,arguments).then(function(r){" +
        "      try{" +
        "        var u=(typeof url==='string')?url:(url&&url.url)||'';" +
        "        if(!/\\/api\\//i.test(u))return r;" +
        "        if(/\\/(payment|quota|balance|credit|user)/i.test(u)){" +
        "          return r.clone().text().then(function(t){" +
        "            var p=window.__freezeResponse(t);" +
        "            if(p!==t){" +
        "              L('RESP-FROZEN: '+u);" +
        "              var h=new Headers();" +
        "              try{r.headers.forEach(function(v,k){" +
        "                if(k.toLowerCase()!=='content-length')h.set(k,v);" +
        "              });}catch(e){}" +
        "              return new Response(p,{status:r.status,statusText:r.statusText,headers:h});" +
        "            }" +
        "            return r;" +
        "          }).catch(function(){return r;});" +
        "        }" +
        "        return r;" +
        "      }catch(e){return r;}" +
        "    });" +
        "  };" +
        "}" +

        // ─── 4. اعتراض XHR
        "var _X=window.XMLHttpRequest;" +
        "if(_X){" +
        "  var _op=_X.prototype.open;var _sd=_X.prototype.send;" +
        "  _X.prototype.open=function(m,u){this.__au=u;return _op.apply(this,arguments);};" +
        "  _X.prototype.send=function(body){" +
        "    var s=this;" +
        "    var u=s.__au||'';" +
        "    var isApi=/\\/api\\//i.test(u);" +
        "    if(isApi&&body&&typeof body==='string'){" +
        "      var patched=window.__freezeRequest(body);" +
        "      if(patched!==body){L('XHR-REQ-PATCHED');arguments[0]=patched;}" +
        "    }" +
        "    if(isApi&&/\\/(payment|quota|balance|credit|user)/i.test(u)){" +
        "      s.addEventListener('readystatechange',function(){" +
        "        if(s.readyState===4){" +
        "          try{" +
        "            var o=s.responseText||'';" +
        "            var p=window.__freezeResponse(o);" +
        "            if(p!==o){L('XHR-RESP-FROZEN');" +
        "              Object.defineProperty(s,'responseText',{value:p,configurable:true});" +
        "              Object.defineProperty(s,'response',{value:p,configurable:true});}" +
        "          }catch(e){}" +
        "        }" +
        "      });" +
        "    }" +
        "    return _sd.apply(this,arguments);" +
        "  };" +
        "}" +

        // ─── 5. حجب add_tracking_event الذي يستهلك الرصيد
        "var _f2=window.fetch;" +
        "if(_f2){" +
        "  var _orig=_f2;" +
        "  window.fetch=function(url,opts){" +
        "    var u=(typeof url==='string')?url:(url&&url.url)||'';" +
        "    if(/\\/api\\/payment\\/add_tracking_event/i.test(u)){" +
        "      L('BLOCKED tracking_event');" +
        "      return Promise.resolve(new Response('{\"status\":0}'," +
        "        {status:200,headers:{'Content-Type':'application/json'}}));" +
        "    }" +
        "    return _orig.apply(this,arguments);" +
        "  };" +
        "}" +

        "L('all freeze layers active');" +
        "})();";
}
