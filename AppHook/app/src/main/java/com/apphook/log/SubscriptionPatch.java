package com.apphook.log;

/**
 * v4.3 — قواعد الترقيع المشتركة (Java mirror لـ patchJson في JS).
 * نفس الـ 30+ قاعدة تُطبق في طبقتين:
 *  1) JS داخل WebView (fetch/XHR/bridge) — المسار الأساسي.
 *  2) Java هنا (Gson/org.json) — دفاع ثانٍ للمسار الـ native.
 *
 * <p>القاعدة: regex ضيقة على أسماء الحقول + فحص سريع مسبق،
 * وكل تعديل يُسجل *PATCHED[...] في السجل.</p>
 */
public final class SubscriptionPatch {
    private SubscriptionPatch() {}

    /** نقاط النهاية الحرجة التي تستحق سطر PATCHED موسوماً بالـ URL */
    private static final String[] CRITICAL_URLS = {
            "/api/user", "/api/payment/get_credit_balance",
            "/api/payment/current_subscriptions", "/api/quota/plan_usage"
    };

    /** فحص سريع: هل النص يستحق المرور على الـ regex؟ */
    public static boolean looksInteresting(String t) {
        if (t == null || t.length() < 8) return false;
        String l = t.length() > 4000 ? t.substring(0, 4000).toLowerCase() : t.toLowerCase();
        boolean sig = l.contains("\"free\"") || l.contains("never_purchased")
                || l.contains("\"balance\"") || l.contains("\"credits\"")
                || l.contains("creditbalance") || l.contains("credit_quota")
                || l.contains("vm_quota") || l.contains("membership") || l.contains("subtype")
                || l.contains("substatus") || l.contains("\"plan\"")
                || l.contains("\"tier\"") || l.contains("is_active")
                || l.contains("ispremium") || l.contains("is_pro") || l.contains("is_vip")
                || l.contains("credit_balance") || l.contains("\"paid\"")
                || l.contains("available");
        if (!sig) return false;
        return l.contains("sub") || l.contains("member") || l.contains("plan")
                || l.contains("balance") || l.contains("credit")
                || l.contains("premium") || l.contains("tier") || l.contains("quota")
                || l.contains("vip") || l.contains("paid");
    }

    /** هل الـ URL من مسار الاشتراك/الدفع؟ (للفلترة والتوسيم) */
    public static boolean isTargetUrl(String url) {
        if (url == null) return false;
        String l = url.toLowerCase();
        if (l.contains("/api/user") || l.contains("/api/payment/")
                || l.contains("/api/quota/") || l.contains("get_credit_balance")
                || l.contains("current_subscriptions") || l.contains("plan_usage")
                || l.contains("tier_config") || l.contains("pending_schedule")
                || l.contains("add_tracking_event") || l.contains("review_reward")) return true;
        return l.contains("/api/") && (l.contains("subscri") || l.contains("member")
                || l.contains("plan") || l.contains("balance") || l.contains("credit")
                || l.contains("premium") || l.contains("pay") || l.contains("billing"));
    }

    public static boolean isCriticalUrl(String url) {
        if (url == null) return false;
        String l = url.toLowerCase();
        for (String c : CRITICAL_URLS) {
            if (l.contains(c)) return true;
        }
        return false;
    }

    private static String r(String p, String from, String to) {
        try {
            return p.replaceAll(from, to);
        } catch (Throwable ignored) {
            return p;
        }
    }

    /**
     * ترقيع JSON كامل — 30+ قاعدة (مطابق لـ patchJson في WebViewHook).
     * يُرجع نفس المرجع إن لم يتغير شيء (للمقارنة بـ !=).
     */
    public static String patchJson(String t) {
        if (!looksInteresting(t)) return t;
        String p = t;

        // 1) الاشتراكات
        p = r(p, "\"subType\"\\s*:\\s*\"free\"", "\"subType\":\"plus\"");
        p = r(p, "\"subStatus\"\\s*:\\s*\"never_purchased\"", "\"subStatus\":\"active\"");
        p = r(p, "\"subStatus\"\\s*:\\s*\"canceled\"", "\"subStatus\":\"active\"");
        p = r(p, "\"subStatus\"\\s*:\\s*\"expired\"", "\"subStatus\":\"active\"");
        p = r(p, "\"membershipType\"\\s*:\\s*\"free\"", "\"membershipType\":\"plus\"");
        p = r(p, "\"membershipStatus\"\\s*:\\s*\"never_purchased\"", "\"membershipStatus\":\"active\"");
        p = r(p, "\"membershipStatus\"\\s*:\\s*\"canceled\"", "\"membershipStatus\":\"active\"");

        // 2) membershipResult وكل حقول membership_type
        p = r(p, "\"membership_type\"\\s*:\\s*\"free\"", "\"membership_type\":\"plus\"");
        p = r(p, "\"personal_membership_type\"\\s*:\\s*\"free\"", "\"personal_membership_type\":\"plus\"");
        p = r(p, "\"personal_paid_membership_type\"\\s*:\\s*\"free\"", "\"personal_paid_membership_type\":\"plus\"");
        p = r(p, "\"web_membership_type\"\\s*:\\s*\"free\"", "\"web_membership_type\":\"plus\"");
        p = r(p, "\"web_paid_membership_type\"\\s*:\\s*\"free\"", "\"web_paid_membership_type\":\"plus\"");
        p = r(p, "\"android_paid_membership_type\"\\s*:\\s*null", "\"android_paid_membership_type\":\"plus\"");
        p = r(p, "\"apple_paid_membership_type\"\\s*:\\s*null", "\"apple_paid_membership_type\":\"plus\"");

        // 3) web_subscription_status
        p = r(p, "\"web_subscription_status\"\\s*:\\s*null", "\"web_subscription_status\":\"active\"");
        p = r(p, "\"web_subscription_id\"\\s*:\\s*null", "\"web_subscription_id\":\"sub_apphook_demo\"");
        p = r(p, "\"vip_membership_type\"\\s*:\\s*null", "\"vip_membership_type\":\"plus\"");

        // 4) personal_membership_ext
        p = r(p, "\"status\"\\s*:\\s*\"never_purchased\"", "\"status\":\"active\"");
        p = r(p, "\"is_active\"\\s*:\\s*false", "\"is_active\":true");
        p = r(p, "\"first_paid_at\"\\s*:\\s*null", "\"first_paid_at\":\"2026-10-15T00:00:00Z\"");
        p = r(p, "\"current_period_start\"\\s*:\\s*null", "\"current_period_start\":\"2026-10-15T00:00:00Z\"");
        p = r(p, "\"current_period_end\"\\s*:\\s*null", "\"current_period_end\":\"2027-10-15T00:00:00Z\"");
        p = r(p, "\"effective_current_period_since\"\\s*:\\s*null", "\"effective_current_period_since\":\"2026-10-15T00:00:00Z\"");

        // 5) الرصيد (حرج — أي قيمة رقمية، الرصيد كان 8 لا 0)
        p = r(p, "\"balance\"\\s*:\\s*[0-9]+", "\"balance\":9999999");
        p = r(p, "\"credit_balance\"\\s*:\\s*[0-9]+", "\"credit_balance\":9999999");
        p = r(p, "\"creditBalance\"\\s*:\\s*[0-9]+", "\"creditBalance\":9999999");
        p = r(p, "\"credits\"\\s*:\\s*[0-9]+(?=[,}])", "\"credits\":9999999");
        p = r(p, "\"credit_quota\"\\s*:\\s*[0-9]+", "\"credit_quota\":9999999");
        p = r(p, "\"creditQuota\"\\s*:\\s*[0-9]+", "\"creditQuota\":9999999");
        p = r(p, "\"remaining\"\\s*:\\s*[0-9]+", "\"remaining\":9999999");
        p = r(p, "\"remaining_balance\"\\s*:\\s*[0-9]+", "\"remaining_balance\":9999999");
        p = r(p, "\"remaining_credits\"\\s*:\\s*[0-9]+", "\"remaining_credits\":9999999");
        p = r(p, "\"available\"\\s*:\\s*[0-9]+", "\"available\":9999999");

        // 5.1) VM quotas
        p = r(p, "\"vm_quota_lite\"\\s*:\\s*[0-9]+", "\"vm_quota_lite\":999");
        p = r(p, "\"vm_quota_standard\"\\s*:\\s*[0-9]+", "\"vm_quota_standard\":999");
        p = r(p, "\"vm_quota_powerful\"\\s*:\\s*[0-9]+", "\"vm_quota_powerful\":999");
        p = r(p, "\"vm_quota_team\"\\s*:\\s*[0-9]+", "\"vm_quota_team\":999");

        // 6) الخطة العامة
        p = r(p, "\"plan\"\\s*:\\s*\"free\"", "\"plan\":\"plus\"");
        p = r(p, "\"personal_plan\"\\s*:\\s*\"free\"", "\"personal_plan\":\"plus\"");
        p = r(p, "\"org_plan\"\\s*:\\s*\"\"", "\"org_plan\":\"plus\"");
        p = r(p, "\"org_active_membership_type\"\\s*:\\s*\"free\"", "\"org_active_membership_type\":\"plus\"");
        p = r(p, "\"org_plan_status\"\\s*:\\s*\"\"", "\"org_plan_status\":\"active\"");
        p = r(p, "\"member_generation\"\\s*:\\s*null", "\"member_generation\":\"plus\"");

        // 7) quota (v4.6: أُزيلت قاعدة "enabled" العامة — واسعة جداً خارج سياق quota)
        p = r(p, "\"pool_funded\"\\s*:\\s*false", "\"pool_funded\":true");
        p = r(p, "\"org_credit_quota_available\"\\s*:\\s*false", "\"org_credit_quota_available\":true");
        p = r(p, "\"has_team_claw\"\\s*:\\s*false", "\"has_team_claw\":true");

        // 8) fallback عام (camel + snake)
        p = r(p, "\"isPremium\"\\s*:\\s*false", "\"isPremium\":true");
        p = r(p, "\"is_premium\"\\s*:\\s*false", "\"is_premium\":true");
        p = r(p, "\"isVip\"\\s*:\\s*false", "\"isVip\":true");
        p = r(p, "\"is_vip\"\\s*:\\s*false", "\"is_vip\":true");
        p = r(p, "\"isPro\"\\s*:\\s*false", "\"isPro\":true");
        p = r(p, "\"is_pro\"\\s*:\\s*false", "\"is_pro\":true");
        p = r(p, "\"isSubscribed\"\\s*:\\s*false", "\"isSubscribed\":true");
        p = r(p, "\"is_subscribed\"\\s*:\\s*false", "\"is_subscribed\":true");
        p = r(p, "\"premium\"\\s*:\\s*false", "\"premium\":true");
        p = r(p, "\"vip\"\\s*:\\s*false", "\"vip\":true");
        p = r(p, "\"paid\"\\s*:\\s*false", "\"paid\":true");
        p = r(p, "\"tier\"\\s*:\\s*\"free\"", "\"tier\":\"plus\"");
        p = r(p, "\"tier\"\\s*:\\s*\"basic\"", "\"tier\":\"plus\"");
        p = r(p, "\"subscription_tier\"\\s*:\\s*\"free\"", "\"subscription_tier\":\"plus\"");
        p = r(p, "\"subscriptionTier\"\\s*:\\s*\"free\"", "\"subscriptionTier\":\"plus\"");

        return p;
    }

    /** ترقيع قيمة getInt للمفاتيح المالية: أي رصيد صغير → 9999999 */
    public static Integer patchInt(String key, Object orig) {
        if (!(orig instanceof Integer)) return null;
        int v = (Integer) orig;
        if (v >= 9999999) return null;
        if (key == null) return null;
        String l = key.toLowerCase();
        if ((l.contains("balance") || l.contains("credit")) && v < 100000) {
            return 9999999;
        }
        return null;
    }

    /** ترقيع قيمة getString لحقول العضوية: "free" → "plus" */
    public static String patchString(String key, Object orig) {
        if (!(orig instanceof String)) return null;
        String s = (String) orig;
        if (key == null) return null;
        String l = key.toLowerCase();
        boolean memberKey = l.contains("membership") || l.contains("plan") || l.contains("tier")
                || l.contains("subtype") || l.contains("sub_type") || l.contains("account_type");
        if (memberKey && s.equalsIgnoreCase("free")) return "plus";
        if (memberKey && s.equalsIgnoreCase("never_purchased")) return "active";
        return null;
    }
}
