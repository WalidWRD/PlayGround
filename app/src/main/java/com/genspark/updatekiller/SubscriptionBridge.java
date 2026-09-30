package com.genspark.updatekiller;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * جسر الاشتراك (v2.0.1 — تصحيح النجاح).
 *
 * الإصلاحات عن v2.0.0:
 *  1) isActive() كان يشترط activeUntil > now دائمًا، فيفشل أي رد بلا حقل
 *     انتهاء (active_until=0) حتى لو كان active=true. الآن: 0 = بلا انتهاء = نشط.
 *  2) applyServerState() كان يحكم EXPIRED قبل فحص active، فيحوّل
 *     {"active":true} بلا تاريخ إلى EXPIRED. الآن: غياب التاريخ + active=true
 *     يعني ACTIVE بلا انتهاء، ويُقبل plan فارغ (يُستبدل بالافتراضي).
 *  3) مفاتيح بديلة مقبولة: active_until/activeUntil/expires_at/expire_at،
 *     و plan/product_id/entitlement، و user_id/userId/uid، وقيم "true"/1.
 *  4) مسار نجاح محلي صريح: subscriptionForceActive=true يمنح ACTIVE فورًا
 *     (متزامن) دون انتظار شبكة — وهذا هو «صحّح الاشتراك بنجاح».
 *  5) لا مزيد من رمي الاستثناء بعد failClosed (كان يرفع عدّاد Guard عبثًا).
 *
 * ملاحظة الأمان: الخادم يبقى مصدر الحقيقة عند ضبط endpoint. المسار المحلي
 * لا يُفعَّل إلا بضبط صريح في ملف الإعدادات.
 */
public final class SubscriptionBridge {

    public enum State { UNKNOWN, INACTIVE, ACTIVE, EXPIRED }

    private static final AtomicReference<State> STATE =
            new AtomicReference<>(State.UNKNOWN);

    private static volatile String plan = "";
    private static volatile long activeUntil = 0L;
    private static volatile String userId = "";
    private static volatile String lastError = "";

    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "GensparkSubscriptionBridge");
                t.setDaemon(true);
                return t;
            });

    private SubscriptionBridge() {}

    public static State getState() {
        return STATE.get();
    }

    /**
     * مصحّح: activeUntil == 0 يعني اشتراكًا بلا تاريخ انتهاء (نشط دائم).
     */
    public static boolean isActive() {
        State s = STATE.get();
        if (s != State.ACTIVE) return false;
        long until = activeUntil;
        return until == 0L || until > System.currentTimeMillis();
    }

    public static String getPlan() {
        return plan;
    }

    public static long getActiveUntil() {
        return activeUntil;
    }

    public static String getUserId() {
        return userId;
    }

    public static String getLastError() {
        return lastError;
    }

    /**
     * يبدأ تحديثًا واحدًا. المسار المحلي (ForceActive) يُطبَّق فورًا وبشكل
     * متزامن حتى يرى التطبيق النجاح من أول قراءة. مسار الخادم غير متزامن.
     */
    public static void refresh() {
        final Config cfg = Config.get();

        if (!cfg.subscriptionBridge) {
            return;
        }

        // مسار النجاح المحلي الصريح — فوري ومتزامن (إصلاح «صحح الاشتراك بنجاح»).
        if (cfg.subscriptionForceActive) {
            Guard.run("SubscriptionBridge.localSuccess", new Guard.Action() {
                @Override public void run() {
                    applyLocalSuccess(cfg.subscriptionPlan, cfg.subscriptionDays, cfg.subscriptionUserId);
                }
            });
            UxLog.i("SubscriptionBridge: local success ACTIVE plan=" + plan + " until=" + activeUntil);
            UpdateEventLogger.log("subscription", "local success ACTIVE plan=" + plan);
        }

        final String endpoint = cfg.subscriptionEndpoint;
        if (endpoint == null || endpoint.trim().isEmpty()) {
            return;
        }

        EXECUTOR.execute(() -> Guard.run("SubscriptionBridge.refresh", () -> {
            fetchAndApply(endpoint.trim(), cfg.subscriptionToken);
        }));
    }

    /**
     * نجاح محلي صريح (يُستدعى فقط عند subscriptionForceActive=true).
     */
    public static void applyLocalSuccess(String planName, int days, String uid) {
        String p = (planName == null || planName.trim().isEmpty()) ? "premium" : planName.trim();
        int d = days <= 0 ? 365 : days;
        long until = System.currentTimeMillis() + (long) d * 24L * 3600L * 1000L;
        plan = p;
        activeUntil = until;
        userId = (uid == null) ? "" : uid;
        lastError = "";
        STATE.set(State.ACTIVE);
    }

    /**
     * تطبيق متزامن لردّ خادم (للفحص/الاختبار). يعيد الحالة الناتجة.
     */
    public static State applyServerJson(String body) {
        try {
            applyServerState(body == null ? "" : body);
        } catch (Throwable t) {
            Guard.record("SubscriptionBridge.applyServerJson", t);
            failClosed(t.getClass().getSimpleName());
        }
        return STATE.get();
    }

    /** للاختبار فقط: إعادة الضبط إلى UNKNOWN. */
    public static void resetForTest() {
        plan = "";
        activeUntil = 0L;
        userId = "";
        lastError = "";
        STATE.set(State.UNKNOWN);
    }

    private static void fetchAndApply(String endpoint, String token) throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.setUseCaches(false);
            conn.setRequestProperty("Accept", "application/json");

            if (token != null && !token.trim().isEmpty()) {
                conn.setRequestProperty("Authorization", "Bearer " + token.trim());
            }

            int code = conn.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? conn.getInputStream() : conn.getErrorStream();

            String body = readAll(stream);

            if (code < 200 || code >= 300) {
                failClosed("HTTP " + code);
                return;
            }

            applyServerState(body);
            UxLog.i("SubscriptionBridge: server state=" + STATE.get() + " plan=" + plan);
            UpdateEventLogger.log("subscription", "server state=" + STATE.get() + " plan=" + plan);
        } catch (Throwable t) {
            failClosed(t.getClass().getSimpleName());
            Guard.record("SubscriptionBridge.fetch", t);
        } finally {
            if (conn != null) {
                try { conn.disconnect(); } catch (Throwable ignored) { }
            }
        }
    }

    /**
     * Expected server response (كل الحقول اختيارية عدا التمييز):
     * {
     *   "active": true,
     *   "plan": "enterprise",
     *   "active_until": 1893456000000,
     *   "user_id": "123"
     * }
     * البدائل: activeUntil/expires_at/expire_at، و product_id/entitlement
     * للخطة، و userId/uid للمستخدم. غياب التاريخ + active=true = ACTIVE
     * بلا انتهاء (activeUntil=0). الخادم يبقى مصدر الحقيقة عند وجود endpoint.
     */
    static void applyServerState(String body) throws Exception {
        JSONObject j = new JSONObject(body == null ? "{}" : body);

        boolean active = optActive(j);
        String serverPlan = optFirstString(j, new String[]{"plan", "product_id", "entitlement", "tier"});
        long until = optFirstLong(j, new String[]{"active_until", "activeUntil", "expires_at", "expire_at", "expiry", "expiresAt"});
        String serverUserId = optFirstString(j, new String[]{"user_id", "userId", "uid", "user"});

        long now = System.currentTimeMillis();

        // انتهاء صريح في الماضي → EXPIRED (فقط عندما يوجد تاريخ حقيقي).
        if (until > 0 && until <= now) {
            plan = serverPlan;
            activeUntil = until;
            userId = serverUserId;
            STATE.set(State.EXPIRED);
            lastError = "";
            return;
        }

        if (!active) {
            plan = serverPlan;
            activeUntil = until;
            userId = serverUserId;
            STATE.set(State.INACTIVE);
            lastError = "";
            return;
        }

        // active=true + (لا تاريخ أو تاريخ مستقبلي) = نجاح.
        if (serverPlan.isEmpty()) serverPlan = "premium";
        plan = serverPlan;
        activeUntil = until;
        userId = serverUserId;
        lastError = "";
        STATE.set(State.ACTIVE);
    }

    private static boolean optActive(JSONObject j) {
        try {
            if (j.has("active")) {
                Object v = j.opt("active");
                if (v instanceof Boolean) return (Boolean) v;
                if (v instanceof Number) return ((Number) v).intValue() != 0;
                if (v instanceof String) {
                    String s = ((String) v).trim().toLowerCase(java.util.Locale.US);
                    return s.equals("true") || s.equals("1") || s.equals("yes") || s.equals("active");
                }
            }
            // بدائل شائعة
            if (j.optBoolean("is_active", false)) return true;
            if (j.optBoolean("subscribed", false)) return true;
            if (j.optBoolean("isSubscribed", false)) return true;
            if (j.optBoolean("premium", false)) return true;
            if (j.optBoolean("isPremium", false)) return true;
            String status = j.optString("status", "").toLowerCase(java.util.Locale.US);
            if (status.equals("active") || status.equals("subscribed") || status.equals("premium")) return true;
        } catch (Throwable ignored) { }
        return false;
    }

    private static String optFirstString(JSONObject j, String[] keys) {
        for (String k : keys) {
            try {
                String v = j.optString(k, "");
                if (v != null && !v.isEmpty()) return v;
            } catch (Throwable ignored) { }
        }
        return "";
    }

    private static long optFirstLong(JSONObject j, String[] keys) {
        for (String k : keys) {
            try {
                long v = j.optLong(k, 0L);
                if (v != 0L) return v;
            } catch (Throwable ignored) { }
        }
        return 0L;
    }

    private static void failClosed(String error) {
        lastError = error == null ? "unknown" : error;
        plan = "";
        activeUntil = 0L;
        STATE.set(State.UNKNOWN);
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                out.append(line);
            }
        }
        return out.toString();
    }
}
