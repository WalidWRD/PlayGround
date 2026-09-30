package com.genspark.updatekiller;

import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * v2.0.2: lifetime subscription + server-verification bypass.
 *
 * - synthesizeActive() now returns LIFETIME (expiry 2100-01-01) instead of +1 year.
 * - coerceSubscribedFields() extended: booleans, status/plan, AND expiry timestamps.
 * - applyLifetimeToJson(): recursive pass used on every server JSON response.
 * - patchServerJsonText(): raw-text regex pass (works even before JSON parsing,
 *   catches "status":"expired"/"free", "plan":"free", expiry dates, entitlements).
 */
public final class SubscriptionBridge {
    private SubscriptionBridge() {
    }

    /** 2100-01-01T00:00:00Z in ms — effectively lifetime. */
    public static final long LIFETIME_EXPIRY_MS = 4102444800000L;
    /** Same in seconds (server APIs often use sec). */
    public static final long LIFETIME_EXPIRY_SEC = 4102444800L;
    public static final String LIFETIME_EXPIRY_ISO = "2100-01-01T00:00:00Z";
    /** Infinite credits — large enough to never deplete, small enough for int. */
    public static final int INFINITE_CREDITS = 999999999;

    public static Object synthesizeActive(String str) {
        try {
            JSONObject out = new JSONObject();
            long now = System.currentTimeMillis();
            String lower = str == null ? "" : str.toLowerCase(Locale.ROOT);
            boolean isAuth = lower.contains("sign") || lower.contains("login");
            boolean isPlan = lower.contains("sub") || lower.contains("plan");

            out.put("subscribed", true);
            out.put("isSubscribed", true);
            out.put("is_subscribed", true);
            out.put("isLoggedIn", true);
            out.put("loggedIn", true);
            out.put("logged_in", true);
            out.put("isPro", true);
            out.put("is_pro", true);
            out.put("isPremium", true);
            out.put("is_premium", true);
            out.put("isPlus", true);
            out.put("is_plus", true);
            out.put("isLifetime", true);
            out.put("lifetime", true);
            out.put("status", "active");
            out.put("subscriptionStatus", "active");
            out.put("plan", "pro");
            out.put("planId", "pro_lifetime");
            out.put("tier", "premium");
            out.put("entitlement", "pro");
            out.put("entitlements", new JSONArray().put("pro"));
            out.put("startedAtMs", now);
            out.put("expiresAtMs", LIFETIME_EXPIRY_MS);
            out.put("expiresAt", LIFETIME_EXPIRY_MS);
            out.put("expiry", LIFETIME_EXPIRY_MS);
            out.put("expiryDate", LIFETIME_EXPIRY_ISO);
            out.put("expiresDate", LIFETIME_EXPIRY_ISO);
            out.put("expiryDateMs", LIFETIME_EXPIRY_MS);
            out.put("currentPeriodEnd", LIFETIME_EXPIRY_SEC);
            out.put("autoRenew", true);
            out.put("trialActive", false);
            // credits: infinity (never exhaust)
            out.put("credits", INFINITE_CREDITS);
            out.put("credit", INFINITE_CREDITS);
            out.put("balance", INFINITE_CREDITS);
            out.put("remainingCredits", INFINITE_CREDITS);
            out.put("remaining_credits", INFINITE_CREDITS);
            out.put("quota", INFINITE_CREDITS);
            out.put("unlimitedCredits", true);
            out.put("unlimited_credits", true);
            // ultra mode: enabled, free (no credit deduction)
            out.put("ultra", true);
            out.put("ultraMode", true);
            out.put("ultra_mode", true);
            out.put("ultraEnabled", true);
            out.put("ultra_enabled", true);
            out.put("ultraAvailable", true);
            out.put("ultraFree", true);
            out.put("deductCredits", false);
            out.put("consumeCredits", false);
            out.put("billingCountry", "LOCAL");
            out.put("provider", isAuth ? "local_subscription" : "local");
            if (isAuth) {
                out.put("id", "sub_user_lifetime");
                out.put("email", "pro@local.user");
                out.put("displayName", "Pro User");
                out.put("idToken", "local-pro-id-token");
                out.put("serverAuthCode", "local-pro-auth");
                out.put("photoUrl", "");
            }
            if (isPlan) {
                JSONArray plans = new JSONArray();
                JSONObject p = new JSONObject();
                p.put("id", "pro_lifetime");
                p.put("name", "Genspark Pro (lifetime)");
                p.put("priceCents", 0);
                p.put("currency", "USD");
                p.put("interval", "lifetime");
                p.put("active", true);
                plans.put(p);
                out.put("plans", plans);
            }
            return out;
        } catch (Throwable th) {
            Guard.record("SubscriptionBridge.synthesize", th);
            return null;
        }
    }

    public static boolean coerceSubscribedFields(Object obj) {
        if (!(obj instanceof JSONObject)) {
            return obj == null ? false : false;
        }
        return applyLifetimeToJson((JSONObject) obj);
    }

    /**
     * Force lifetime subscription state into a server JSON object.
     * Returns true if anything was changed. Never throws.
     */
    public static boolean applyLifetimeToJson(JSONObject o) {
        if (o == null) {
            return false;
        }
        boolean changed = false;
        try {
            String[] trueBools = {"subscribed", "isSubscribed", "is_subscribed",
                    "isLoggedIn", "loggedIn", "logged_in",
                    "isPro", "is_pro", "isPlus", "is_plus",
                    "isPremium", "is_premium", "isSubscribedUser", "is_subscriber",
                    "hasPro", "has_pro", "hasPremium", "has_premium",
                    "premium", "pro", "vip", "isVip", "is_vip"};
            for (String k : trueBools) {
                try {
                    if (o.has(k) && !o.optBoolean(k, false)) {
                        o.put(k, true);
                        changed = true;
                    }
                } catch (Throwable ignored) {
                }
            }
            // status-like strings -> active
            String[] activeKeys = {"status", "subscriptionStatus", "subStatus",
                    "subscription_status", "state", "accountStatus", "membershipStatus"};
            for (String k : activeKeys) {
                try {
                    if (o.has(k) && !"active".equals(o.optString(k, ""))) {
                        o.put(k, "active");
                        changed = true;
                    }
                } catch (Throwable ignored) {
                }
            }
            // plan-like strings -> pro
            String[] planKeys = {"plan", "planId", "plan_id", "tier", "entitlement", "productId"};
            for (String k : planKeys) {
                try {
                    if (o.has(k)) {
                        String v = o.optString(k, "");
                        if (v == null || v.isEmpty() || "free".equalsIgnoreCase(v) || "basic".equalsIgnoreCase(v)) {
                            o.put(k, "pro_lifetime".equals(k) || "planId".equals(k) ? "pro_lifetime" : "pro");
                            changed = true;
                        } else if ("plan".equals(k) && !"pro".equalsIgnoreCase(v)) {
                            o.put(k, "pro");
                            changed = true;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            // lifetime flags
            String[] lifeKeys = {"isLifetime", "lifetime", "lifetimeAccess", "permanent"};
            for (String k : lifeKeys) {
                try {
                    if (o.has(k) && !o.optBoolean(k, false)) {
                        o.put(k, true);
                        changed = true;
                    }
                } catch (Throwable ignored) {
                }
            }
            // CREDITS -> infinity (server "رصيدك/credit exhausted" bypass)
            String[] creditKeys = {"credits", "credit", "balance", "remainingCredits",
                    "remaining_credits", "remainingCredit", "remaining_credit",
                    "quota", "quotaLeft", "quota_left", "points", "coins",
                    "freeCredits", "free_credits", "totalCredits", "total_credits"};
            for (String k : creditKeys) {
                try {
                    if (o.has(k) && o.optLong(k, 0L) != (long) INFINITE_CREDITS) {
                        o.put(k, INFINITE_CREDITS);
                        changed = true;
                    }
                } catch (Throwable ignored) {
                }
            }
            String[] unlimitedKeys = {"unlimitedCredits", "unlimited_credits", "unlimited"};
            for (String k : unlimitedKeys) {
                try {
                    if (o.has(k) && !o.optBoolean(k, false)) {
                        o.put(k, true);
                        changed = true;
                    }
                } catch (Throwable ignored) {
                }
            }
            // ULTRA MODE -> enabled + free (no deduction)
            String[] ultraKeys = {"ultra", "ultraMode", "ultra_mode", "ultraEnabled",
                    "ultra_enabled", "ultraAvailable", "ultra_available",
                    "ultraFree", "ultra_free", "ultraAccess", "canUseUltra"};
            for (String k : ultraKeys) {
                try {
                    if (o.has(k) && !o.optBoolean(k, false)) {
                        o.put(k, true);
                        changed = true;
                    }
                } catch (Throwable ignored) {
                }
            }
            String[] noDeductKeys = {"deductCredits", "consumeCredits", "deduct_credits",
                    "consume_credits", "chargeCredits"};
            for (String k : noDeductKeys) {
                try {
                    if (o.has(k) && o.optBoolean(k, true)) {
                        o.put(k, false);
                        changed = true;
                    }
                } catch (Throwable ignored) {
                }
            }
            // expiry timestamps -> lifetime (ms or sec, any numeric type)
            String[] expMs = {"expiresAtMs", "expiresAt", "expiry", "expiryDateMs",
                    "expiryMs", "expireAtMs", "subscriptionExpiresAt", "currentPeriodEndMs"};
            for (String k : expMs) {
                try {
                    if (o.has(k) && o.optLong(k, 0L) != LIFETIME_EXPIRY_MS) {
                        o.put(k, LIFETIME_EXPIRY_MS);
                        changed = true;
                    }
                } catch (Throwable ignored) {
                }
            }
            String[] expSec = {"expiresAtSec", "expirySec", "currentPeriodEnd",
                    "expires_at", "exp", "expiry_date"};
            for (String k : expSec) {
                try {
                    if (o.has(k)) {
                        long v = o.optLong(k, 0L);
                        if (v != 0L && v != LIFETIME_EXPIRY_SEC && v != LIFETIME_EXPIRY_MS) {
                            o.put(k, v > 100000000000L ? LIFETIME_EXPIRY_MS : LIFETIME_EXPIRY_SEC);
                            changed = true;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
            // ISO date strings -> lifetime
            String[] isoKeys = {"expiryDate", "expiresDate", "expiry_date", "expires_date",
                    "expirationDate", "validUntil", "valid_until"};
            for (String k : isoKeys) {
                try {
                    if (o.has(k) && !LIFETIME_EXPIRY_ISO.equals(o.optString(k, ""))) {
                        o.put(k, LIFETIME_EXPIRY_ISO);
                        changed = true;
                    }
                } catch (Throwable ignored) {
                }
            }
            // verified flags
            String[] verifiedKeys = {"verified", "isVerified", "serverVerified", "purchaseVerified"};
            for (String k : verifiedKeys) {
                try {
                    if (o.has(k) && !o.optBoolean(k, false)) {
                        o.put(k, true);
                        changed = true;
                    }
                } catch (Throwable ignored) {
                }
            }
            // recurse into nested objects / arrays (server payloads are nested)
            JSONArray names = o.names();
            if (names != null) {
                for (int i = 0; i < names.length(); i++) {
                    String key = names.optString(i, null);
                    if (key == null) {
                        continue;
                    }
                    try {
                        JSONObject child = o.optJSONObject(key);
                        if (child != null && applyLifetimeToJson(child)) {
                            changed = true;
                        }
                        JSONArray arr = o.optJSONArray(key);
                        if (arr != null) {
                            for (int j = 0; j < arr.length(); j++) {
                                JSONObject item = arr.optJSONObject(j);
                                if (item != null && applyLifetimeToJson(item)) {
                                    changed = true;
                                }
                            }
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
        } catch (Throwable t) {
            Guard.record("SubscriptionBridge.lifetime", t);
        }
        return changed;
    }

    /**
     * Raw-text pass for server JSON: catches verification payloads before parsing.
     * Forces false->true for sub flags, expired/free->active/pro, and any past
     * expiry into the lifetime timestamp.
     */
    public static String patchServerJsonText(String s) {
        if (s == null || s.isEmpty() || s.length() < 8) {
            return s;
        }
        // fast path: must look like a subscription/verification payload
        String low = s.toLowerCase(Locale.ROOT);
        boolean looksSub = low.contains("subscri") || low.contains("\"status\"")
                || low.contains("\"plan\"") || low.contains("expir")
                || low.contains("entitle") || low.contains("ispro")
                || low.contains("is_pro") || low.contains("premium")
                || low.contains("credit") || low.contains("balance")
                || low.contains("quota") || low.contains("ultra");
        if (!looksSub) {
            return s;
        }
        String out = s;
        try {
            String[] boolKeys = {"subscribed", "isSubscribed", "is_subscribed",
                    "isLoggedIn", "loggedIn", "logged_in",
                    "isPro", "is_pro", "isPlus", "is_plus",
                    "isPremium", "is_premium", "isVip", "is_vip",
                    "isLifetime", "lifetime", "verified", "isVerified",
                    "ultra", "ultraMode", "ultra_mode", "ultraEnabled",
                    "ultra_enabled", "ultraAvailable", "ultraFree",
                    "unlimitedCredits", "unlimited_credits", "unlimited"};
            for (String k : boolKeys) {
                out = out.replaceAll("(\"" + java.util.regex.Pattern.quote(k) + "\"\\s*:\\s*)false",
                        "$1true");
            }
            // credits exhausted (0 / small) -> infinity
            out = out.replaceAll("(\"(?:credits|credit|balance|remainingCredits|remaining_credits|quota)\"\\s*:\\s*)(?:0|[1-9][0-9]{0,5})(\\b)",
                    "$1" + INFINITE_CREDITS + "$2");
            // status / state -> active (expired, inactive, free, basic, trial...)
            out = out.replaceAll("(\"(?:status|subscriptionStatus|subStatus|subscription_status|state|accountStatus|membershipStatus)\"\\s*:\\s*\")(?:expired|inactive|free|basic|trial|cancelled|canceled|past_due|unpaid|none)(\\s*\"?)",
                    "$1active$2");
            // plan / tier -> pro
            out = out.replaceAll("(\"(?:plan|tier|entitlement)\"\\s*:\\s*\")(?:free|basic|standard|none)(\\s*\"?)",
                    "$1pro$2");
        } catch (Throwable t) {
            Guard.record("SubscriptionBridge.patchText", t);
            return s;
        }
        return out;
    }

    /** Infinite-credit payload for MethodChannel credit/balance queries. */
    public static Object synthesizeCredits(String method) {
        try {
            JSONObject out = new JSONObject();
            out.put("credits", INFINITE_CREDITS);
            out.put("credit", INFINITE_CREDITS);
            out.put("balance", INFINITE_CREDITS);
            out.put("remainingCredits", INFINITE_CREDITS);
            out.put("remaining_credits", INFINITE_CREDITS);
            out.put("quota", INFINITE_CREDITS);
            out.put("unlimitedCredits", true);
            out.put("unlimited_credits", true);
            out.put("ultra", true);
            out.put("ultraMode", true);
            out.put("ultraEnabled", true);
            out.put("ultraFree", true);
            out.put("deductCredits", false);
            out.put("method", method == null ? "" : method);
            return out;
        } catch (Throwable t) {
            Guard.record("SubscriptionBridge.credits", t);
            return INFINITE_CREDITS;
        }
    }

    /** Should this getter key be spoofed at org.json level (server bypass)? */
    public static boolean isServerSpoofKey(String key) {
        if (key == null) {
            return false;
        }
        String k = key.toLowerCase(Locale.ROOT);
        return k.contains("subscri") || k.contains("ispro") || k.contains("is_pro")
                || k.contains("isplus") || k.contains("is_plus")
                || k.contains("ispremium") || k.contains("is_premium")
                || k.contains("islifetime") || k.equals("lifetime")
                || k.equals("status") || k.contains("subscriptionstatus")
                || k.equals("plan") || k.equals("planid") || k.equals("tier")
                || k.contains("expir") || k.contains("entitle")
                || k.contains("verified") || k.equals("vip")
                || k.contains("credit") || k.contains("balance") || k.contains("quota")
                || k.contains("coin") || k.contains("point")
                || k.contains("ultra");
    }
}
