package com.genspark.updatekiller.json;

import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Guard;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.regex.Pattern;

/**
 * يُعطّل حقول قرار التحديث في أي بنية JSON أو نصّ:
 * forceUpgrade → false · minAppVersionCode → 0 · minAppRequireAppVersion → ""
 * + (v2.0.1) تصحيح الاشتراك: isPremium/isSubscribed/active → true،
 * وحقول الانتهاء → مستقبل بعيد، عند تفعيل جسر الاشتراك.
 * لا يرمي أبدًا (يعمل داخل Guard)؛ أي فشل يُسجَّل ويُعاد النصّ الأصلي كما هو.
 */
public final class JsonNeutralizer {

    /** مفاتيح الاشتراك المنطقية → true عند نجاح الاشتراك. */
    public static final String[] SUB_BOOL_KEYS = {
        "isPremium", "premium", "isSubscribed", "subscribed", "is_subscribed",
        "isPro", "pro", "vip", "isVip", "entitled", "isEntitled",
        "active", "is_active", "hasSubscription", "has_subscription"
    };

    /** مفاتيح انتهاء الاشتراك → تاريخ مستقبلي بعيد. */
    public static final String[] SUB_EXPIRY_KEYS = {
        "active_until", "activeUntil", "expires_at", "expire_at",
        "expiry", "expiresAt", "expiryDate", "expiry_date"
    };

    /** تاريخ انتهاء بعيد ثابت (01-01-2035 UTC). */
    public static final long FAR_FUTURE = 2051222400000L;

    private JsonNeutralizer() { }

    /** تعديل بنيوي عميق على JSONObject. يعيد true إن تغيّر شيء. */
    public static boolean apply(JSONObject o) {
        if (o == null) return false;
        final Config cfg = Config.get();
        final boolean[] changed = { false };
        Guard.run("JsonObject", () -> walk(o, cfg, changed));
        return changed[0];
    }

    private static void walk(JSONObject o, Config cfg, boolean[] changed) throws Throwable {
        JSONArray names = o.names();
        if (names == null) return;
        for (int i = 0; i < names.length(); i++) {
            String k = names.optString(i, null);
            if (k == null) continue;
            if (cfg.hasKey(k)) {
                if (isBool(k)) {
                    if (o.optBoolean(k, false)) { o.put(k, false); changed[0] = true; }
                } else if (isInt(k)) {
                    if (o.optInt(k, 0) != 0) { o.put(k, 0); changed[0] = true; }
                } else {
                    if (o.optString(k, "").length() > 0) { o.put(k, ""); changed[0] = true; }
                }
            } else {
                JSONObject sub = o.optJSONObject(k);
                if (sub != null) walk(sub, cfg, changed);
                JSONArray subA = o.optJSONArray(k);
                if (subA != null) walk(subA, cfg, changed);
            }
        }
    }

    private static void walk(JSONArray a, Config cfg, boolean[] changed) throws Throwable {
        for (int i = 0; i < a.length(); i++) {
            JSONObject sub = a.optJSONObject(i);
            if (sub != null) walk(sub, cfg, changed);
            JSONArray subA = a.optJSONArray(i);
            if (subA != null) walk(subA, cfg, changed);
        }
    }

    /** تعديل نصّي احتياطي عندما لا يكون الردّ JSON صالحًا. يعيد النصّ (المعدَّل أو الأصلي). */
    public static String applyToText(String s) {
        if (s == null || s.isEmpty()) return s;
        final Config cfg = Config.get();
        return Guard.value("JsonText", () -> {
            boolean likely = false;
            for (String k : cfg.keys) if (k != null && s.contains("\"" + k + "\"")) { likely = true; break; }
            if (!likely) return s;

            String out = s;
            for (String k : cfg.keys) {
                if (k == null) continue;
                String key = Pattern.quote(k);
                out = out.replaceAll("(\"" + key + "\"\\s*:\\s*)true", "$1false");
                out = out.replaceAll("(\"" + key + "\"\\s*:\\s*)[1-9][0-9]*", "$10");
                out = out.replaceAll("(\"" + key + "\"\\s*:\\s*)\"[^\"]*\"", "$1\"\"");
            }
            return out;
        }, s);
    }

    public static boolean changedText(String before, String after) {
        // Guard قد يُعيد النصّ نفسه عند الفشل؛ نقارن فقط.
        return before != null && after != null && !before.equals(after);
    }

    /* ── تصحيح الاشتراك (v2.0.1) ── */

    /** هل هذا المفتاح منطقي للاشتراك؟ */
    public static boolean isSubBool(String k) {
        if (k == null) return false;
        for (String s : SUB_BOOL_KEYS) if (s.equals(k)) return true;
        return false;
    }

    /** هل هذا المفتاح تاريخ انتهاء اشتراك؟ */
    public static boolean isSubExpiry(String k) {
        if (k == null) return false;
        for (String s : SUB_EXPIRY_KEYS) if (s.equals(k)) return true;
        return false;
    }

    /**
     * تعديل بنيوي لاشتراك ناجح: bools → true، expiry → مستقبل بعيد.
     * يعيد true إن تغيّر شيء. لا يفعل شيئًا عندما لا يكون الاشتراك مفعّلًا
     * وناجحًا (يُفحص عبر الدالتين الممرّرتين لتفادي الاعتماد على Config داخل
     * الاختبارات).
     */
    public static boolean applySubscription(JSONObject o, boolean enabled, boolean active) {
        if (o == null || !enabled || !active) return false;
        final boolean[] changed = { false };
        Guard.run("JsonSubscription", () -> walkSubscription(o, changed));
        return changed[0];
    }

    private static void walkSubscription(JSONObject o, boolean[] changed) throws Throwable {
        JSONArray names = o.names();
        if (names == null) return;
        for (int i = 0; i < names.length(); i++) {
            String k = names.optString(i, null);
            if (k == null) continue;
            if (isSubBool(k)) {
                if (!o.optBoolean(k, false)) { o.put(k, true); changed[0] = true; }
            } else if (isSubExpiry(k)) {
                long v = o.optLong(k, 0L);
                if (v <= System.currentTimeMillis()) { o.put(k, FAR_FUTURE); changed[0] = true; }
            } else {
                JSONObject sub = o.optJSONObject(k);
                if (sub != null) walkSubscription(sub, changed);
                JSONArray subA = o.optJSONArray(k);
                if (subA != null) walkSubscription(subA, changed);
            }
        }
    }

    private static void walkSubscription(JSONArray a, boolean[] changed) throws Throwable {
        for (int i = 0; i < a.length(); i++) {
            JSONObject sub = a.optJSONObject(i);
            if (sub != null) walkSubscription(sub, changed);
            JSONArray subA = a.optJSONArray(i);
            if (subA != null) walkSubscription(subA, changed);
        }
    }

    /** تعديل نصّي لاشتراك ناجح (احتياطي بلا JSON صالح). */
    public static String applySubscriptionToText(String s, boolean enabled, boolean active) {
        if (s == null || s.isEmpty() || !enabled || !active) return s;
        return Guard.value("JsonSubscriptionText", () -> {
            boolean likely = false;
            for (String k : SUB_BOOL_KEYS) if (s.contains("\"" + k + "\"")) { likely = true; break; }
            if (!likely) {
                for (String k : SUB_EXPIRY_KEYS) if (s.contains("\"" + k + "\"")) { likely = true; break; }
            }
            if (!likely) return s;
            String out = s;
            for (String k : SUB_BOOL_KEYS) {
                if (k == null) continue;
                String key = Pattern.quote(k);
                out = out.replaceAll("(\"" + key + "\"\\s*:\\s*)false", "$1true");
            }
            for (String k : SUB_EXPIRY_KEYS) {
                if (k == null) continue;
                String key = Pattern.quote(k);
                out = out.replaceAll("(\"" + key + "\"\\s*:\\s*)[0-9]+", "$1" + FAR_FUTURE);
            }
            return out;
        }, s);
    }

    public static boolean isBool(String k) {
        return "forceUpgrade".equals(k) || "forceUpgradeD".equals(k) || "directUpgrade".equals(k);
    }
    public static boolean isInt(String k) { return "minAppVersionCode".equals(k); }
    public static boolean isStr(String k) {
        return "minAppRequireAppVersion".equals(k) || "requiresAppVersion".equals(k);
    }
}
