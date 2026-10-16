package com.apphook.log.hooks;

import com.apphook.log.Config;
import com.apphook.log.FingerprintEngine;
import com.apphook.log.SafeHook;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;

/**
 * Features #10-11: SharedPreferences — تسجيل دائم + ترقيع اختياري (F002/F006).
 * v6.3: مفاتيح موسّعة + getLong + قيم موحّدة (FROZEN_BALANCE/FORCED_PLAN).
 */
public final class PrefsHook {
    private PrefsHook() {}

    public static void install(XposedInterface fw, ClassLoader cl) {
        Class<?> sp = FingerprintEngine.findClassQuiet(cl,
                "android.app.SharedPreferencesImpl");
        if (sp == null) {
            try {
                sp = Class.forName("android.app.SharedPreferencesImpl");
            } catch (Throwable ignored) {}
        }
        if (sp == null) {
            SafeHook.logSafe("Prefs", "SharedPreferencesImpl absent");
            return;
        }
        // ═══ getBoolean ═══
        Method b = FingerprintEngine.findMethodByFingerprint(sp,
                "getBoolean", boolean.class, 2);
        HookKit.hookOne(fw, b, "Prefs.getBoolean", chain -> {
            Object orig = chain.proceed();
            try {
                String k = (String) chain.getArg(0);
                Object patched = orig;
                if (Config.PATCH_PREFS && isPremiumBool(k) && Boolean.FALSE.equals(orig)) {
                    patched = true;
                    SafeHook.countOpen("prefs.bool:" + k, "F006");
                }
                if (isPremiumBool(k)) {
                    SafeHook.recordCall("Prefs", "getBoolean(" + k + ")", 2, orig, patched);
                    if (patched != orig) return patched;
                }
            } catch (Throwable t) {
                SafeHook.logError("Prefs.getBoolean", t);
            }
            return orig;
        });
        // ═══ getInt ═══
        Method gi = FingerprintEngine.findMethodByFingerprint(sp,
                "getInt", int.class, 2);
        HookKit.hookOne(fw, gi, "Prefs.getInt", chain -> {
            Object orig = chain.proceed();
            try {
                String k = (String) chain.getArg(0);
                Object patched = orig;
                if (Config.PATCH_PREFS && orig instanceof Integer) {
                    int v = (Integer) orig;
                    if (isBalanceKey(k) && v < 1000000) {
                        patched = Config.FROZEN_BALANCE;
                        SafeHook.countOpen("prefs.int.balance:" + k, "F002");
                        if (Config.LOG_ORIGINAL_BALANCE) {
                            SafeHook.log("Prefs: getInt(" + k + ") ORIG=" + v
                                    + " -> NEW=" + patched + " ★");
                        }
                    } else if (isPremiumBool(k) && v == 0) {
                        patched = 1;
                        SafeHook.countOpen("prefs.int.flag:" + k, "F006");
                    }
                }
                if (isPremiumBool(k) || isBalanceKey(k)) {
                    SafeHook.recordCall("Prefs", "getInt(" + k + ")", 2, orig, patched);
                    if (patched != orig) return patched;
                }
            } catch (Throwable t) {
                SafeHook.logError("Prefs.getInt", t);
            }
            return orig;
        });
        // ═══ getLong (بعض التطبيقات تستخدم Long) ═══
        Method gl = FingerprintEngine.findMethodByFingerprint(sp,
                "getLong", long.class, 2);
        HookKit.hookOne(fw, gl, "Prefs.getLong", chain -> {
            Object orig = chain.proceed();
            try {
                String k = (String) chain.getArg(0);
                Object patched = orig;
                if (Config.PATCH_PREFS && orig instanceof Long
                        && isBalanceKey(k) && ((Long) orig) < 1000000L) {
                    patched = (long) Config.FROZEN_BALANCE;
                    SafeHook.countOpen("prefs.long.balance:" + k, "F002");
                }
                if (isBalanceKey(k)) {
                    SafeHook.recordCall("Prefs", "getLong(" + k + ")", 2, orig, patched);
                    if (patched != orig) return patched;
                }
            } catch (Throwable t) {
                SafeHook.logError("Prefs.getLong", t);
            }
            return orig;
        });
        // ═══ getString (JSON) ═══
        Method gs = FingerprintEngine.findMethodByFingerprint(sp,
                "getString", String.class, 2);
        HookKit.hookOne(fw, gs, "Prefs.getString", chain -> {
            Object orig = chain.proceed();
            try {
                String k = (String) chain.getArg(0);
                if (!(orig instanceof String)) return orig;
                String s = (String) orig;
                if (!isPremiumKey(k) && !containsPremium(s) && !isBalanceKey(k)) {
                    return orig;
                }
                Object patched = orig;
                if (Config.PATCH_PREFS) {
                    String q = patchJson(s);
                    if (q != s) {
                        patched = q;
                        SafeHook.countOpen("prefs.str:" + k, "F006");
                    }
                }
                SafeHook.recordCall("Prefs", "getString(" + k + ")", 2,
                        abbrev(s), abbrev(patched));
                return patched;
            } catch (Throwable t) {
                SafeHook.logError("Prefs.getString", t);
            }
            return orig;
        });
        SafeHook.log("Prefs: hooked v6.3 (bool/int/long/string)");
    }

    // ═══ مطابقة المفاتيح ═══
    public static boolean isBalanceKey(String k) {
        if (k == null) return false;
        String l = k.toLowerCase();
        return l.contains("balance") || l.contains("credit")
                || l.contains("quota") || l.contains("remaining")
                || l.contains("available") || l.contains("token_count")
                || l.contains("credit_balance");
    }

    static boolean isPremiumBool(String k) {
        if (k == null) return false;
        String l = k.toLowerCase();
        return l.contains("premium") || l.contains("vip") || l.contains("pro")
                || l.contains("paid") || l.contains("subscri") || l.contains("entitle")
                || l.contains("is_active") || l.contains("isactive");
    }

    public static boolean isPremiumKey(String k) {
        if (k == null) return false;
        String l = k.toLowerCase();
        return l.contains("premium") || l.contains("vip") || l.contains("pro")
                || l.contains("paid") || l.contains("subscri") || l.contains("entitle")
                || l.contains("plan") || l.contains("membership") || l.contains("tier");
    }

    public static boolean containsPremium(String s) {
        if (s == null || s.length() < 5) return false;
        return s.contains("plan") || s.contains("balance") || s.contains("credit")
                || s.contains("premium") || s.contains("isPremium") || s.contains("vip")
                || s.contains("subscri") || s.contains("membership") || s.contains("tier");
    }

    // ═══ Patch JSON – القيم الموحَّدة ═══
    static String patchJson(String s) {
        String p = s;
        String BV = Config.FROZEN_BALANCE_STR;
        String F = Config.FORCED_PLAN;
        try {
            // ─── Balance ───
            p = p.replaceAll("\"balance\":\\s*-?[0-9]+", "\"balance\":" + BV);
            p = p.replaceAll("\"credit_balance\":\\s*-?[0-9]+", "\"credit_balance\":" + BV);
            p = p.replaceAll("\"creditBalance\":\\s*-?[0-9]+", "\"creditBalance\":" + BV);
            p = p.replaceAll("\"credits\":\\s*-?[0-9]+(?=[,}])", "\"credits\":" + BV);
            p = p.replaceAll("\"credit\":\\s*-?[0-9]+", "\"credit\":" + BV);
            p = p.replaceAll("\"remaining\":\\s*-?[0-9]+", "\"remaining\":" + BV);
            p = p.replaceAll("\"remaining_balance\":\\s*-?[0-9]+", "\"remaining_balance\":" + BV);
            p = p.replaceAll("\"remaining_credits\":\\s*-?[0-9]+", "\"remaining_credits\":" + BV);
            p = p.replaceAll("\"available\":\\s*-?[0-9]+", "\"available\":" + BV);
            p = p.replaceAll("\"available_credits\":\\s*-?[0-9]+", "\"available_credits\":" + BV);
            p = p.replaceAll("\"balanceValue\":\\s*-?[0-9]+", "\"balanceValue\":" + BV);
            p = p.replaceAll("\"total_credits\":\\s*-?[0-9]+", "\"total_credits\":" + BV);

            // ─── Consumed → 0 ───
            p = p.replaceAll("\"consumed\":\\s*[0-9]+", "\"consumed\":0");
            p = p.replaceAll("\"used\":\\s*[0-9]+", "\"used\":0");
            p = p.replaceAll("\"used_credits\":\\s*[0-9]+", "\"used_credits\":0");
            p = p.replaceAll("\"spent\":\\s*[0-9]+", "\"spent\":0");
            p = p.replaceAll("\"quota_used\":\\s*[0-9]+", "\"quota_used\":0");
            p = p.replaceAll("\"quota_consumed\":\\s*[0-9]+", "\"quota_consumed\":0");

            // ─── Plan ───
            p = p.replaceAll("\"plan\":\\s*\"free\"", "\"plan\":\"" + F + "\"");
            p = p.replaceAll("\"personal_plan\":\\s*\"free\"", "\"personal_plan\":\"" + F + "\"");
            p = p.replaceAll("\"org_plan\":\\s*\"\"", "\"org_plan\":\"" + F + "\"");
            p = p.replaceAll("\"subType\":\\s*\"free\"", "\"subType\":\"" + F + "\"");
            p = p.replaceAll("\"sub_type\":\\s*\"free\"", "\"sub_type\":\"" + F + "\"");
            p = p.replaceAll("\"membershipType\":\\s*\"free\"", "\"membershipType\":\"" + F + "\"");
            p = p.replaceAll("\"membership_type\":\\s*\"free\"", "\"membership_type\":\"" + F + "\"");
            p = p.replaceAll("\"personal_membership_type\":\\s*\"free\"", "\"personal_membership_type\":\"" + F + "\"");
            p = p.replaceAll("\"personal_paid_membership_type\":\\s*\"free\"", "\"personal_paid_membership_type\":\"" + F + "\"");
            p = p.replaceAll("\"web_membership_type\":\\s*\"free\"", "\"web_membership_type\":\"" + F + "\"");
            p = p.replaceAll("\"web_paid_membership_type\":\\s*\"free\"", "\"web_paid_membership_type\":\"" + F + "\"");
            p = p.replaceAll("\"tier\":\\s*\"free\"", "\"tier\":\"" + F + "\"");
            p = p.replaceAll("\"subscription_tier\":\\s*\"free\"", "\"subscription_tier\":\"" + F + "\"");
            p = p.replaceAll("\"user_plan\":\\s*\"free\"", "\"user_plan\":\"" + F + "\"");
            p = p.replaceAll("\"account_type\":\\s*\"free\"", "\"account_type\":\"" + F + "\"");
            p = p.replaceAll("\"accountType\":\\s*\"free\"", "\"accountType\":\"" + F + "\"");
            p = p.replaceAll("\"user_type\":\\s*\"free\"", "\"user_type\":\"" + F + "\"");
            p = p.replaceAll("\"userType\":\\s*\"free\"", "\"userType\":\"" + F + "\"");
            p = p.replaceAll("\"display_plan\":\\s*\"Free\"", "\"display_plan\":\"" + Config.FORCED_PLAN_DISPLAY + "\"");
            p = p.replaceAll("\"displayPlan\":\\s*\"Free\"", "\"displayPlan\":\"" + Config.FORCED_PLAN_DISPLAY + "\"");
            p = p.replaceAll("\"plan_name\":\\s*\"Free\"", "\"plan_name\":\"" + Config.FORCED_PLAN_DISPLAY + "\"");

            // ─── Status ───
            p = p.replaceAll("\"status\":\\s*\"never_purchased\"", "\"status\":\"" + Config.FORCED_STATUS + "\"");
            p = p.replaceAll("\"subStatus\":\\s*\"never_purchased\"", "\"subStatus\":\"" + Config.FORCED_STATUS + "\"");
            p = p.replaceAll("\"membershipStatus\":\\s*\"never_purchased\"", "\"membershipStatus\":\"" + Config.FORCED_STATUS + "\"");
            p = p.replaceAll("\"membership_status\":\\s*\"never_purchased\"", "\"membership_status\":\"" + Config.FORCED_STATUS + "\"");

            // ─── Boolean flags ───
            p = p.replaceAll("\"is_active\":\\s*false", "\"is_active\":true");
            p = p.replaceAll("\"isPremium\":\\s*false", "\"isPremium\":true");
            p = p.replaceAll("\"is_premium\":\\s*false", "\"is_premium\":true");
            p = p.replaceAll("\"isVip\":\\s*false", "\"isVip\":true");
            p = p.replaceAll("\"is_vip\":\\s*false", "\"is_vip\":true");
            p = p.replaceAll("\"isPro\":\\s*false", "\"isPro\":true");
            p = p.replaceAll("\"is_pro\":\\s*false", "\"is_pro\":true");
            p = p.replaceAll("\"isSubscribed\":\\s*false", "\"isSubscribed\":true");
            p = p.replaceAll("\"is_subscribed\":\\s*false", "\"is_subscribed\":true");
            p = p.replaceAll("\"isPaid\":\\s*false", "\"isPaid\":true");
            p = p.replaceAll("\"is_paid\":\\s*false", "\"is_paid\":true");
            p = p.replaceAll("\"premium\":\\s*false", "\"premium\":true");
            p = p.replaceAll("\"vip\":\\s*false", "\"vip\":true");
            p = p.replaceAll("\"org_active_membership_type\":\\s*\"free\"",
                    "\"org_active_membership_type\":\"" + F + "\"");
            p = p.replaceAll("\"org_credit_quota_available\":\\s*false",
                    "\"org_credit_quota_available\":true");
        } catch (Throwable ignored) {}
        return p;
    }

    private static String abbrev(Object o) {
        if (o == null) return "null";
        String s;
        try {
            s = String.valueOf(o);
        } catch (Throwable ignored) {
            return "?";
        }
        return s.length() > 100 ? s.substring(0, 100) + "..." : s;
    }
}
