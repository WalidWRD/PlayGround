package com.genspark.updatekiller;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;

/**
 * إعدادات الموديول (v2.0.0) — كل القيم اختيارية وأي خطأ قراءة يُتجاهل (لا كراش).
 * المسارات بالترتيب:
 *   1) /data/local/tmp/genspark_updatekiller.json
 *   2) /sdcard/Download/genspark_updatekiller.json
 *   3) /sdcard/Android/data/&lt;pkg&gt;/files/genspark_updatekiller.json
 *
 * إعادة تحميل تلقائية (Hot-Reload) كل 3 ثوانٍ عند تغيّر آخر تعديل.
 */
public final class Config {

    /** false = إيقاف الموديول بالكامل. */
    public boolean enabled = true;

    /* ── الآلية الحاسمة: تزوير الإصدار ── */
    public boolean spoofVersion = true;
    public int    spoofVersionCode = 9999999;
    public String spoofVersionName = "9.99.9";

    /* ── محرّك الاستكشاف الديناميكي (جديد 2.0.0) ── */
    /** فهرسة أسماء الفئات من DEX الهدف (يمكّن الاكتشاف البنيوي بدل الأسماء الثابتة). */
    public boolean classScan = true;
    /** حدّ أقصى لعدد الأسماء المفهرسة. */
    public int scanMaxNames = 250000;
    /** تثبيت مُؤجَّل متعدّد المرور + إعادة محاولة عند تحميل الفئات (للتطبيقات المحمية). */
    public boolean lazyInstall = true;
    /** ربط PackageManager المكتشَف ديناميكيًا (تزوير إصدار مُعمَّق لكل الإصدارات). */
    public boolean pmDiscovery = true;
    /** اكتشاف قنوات Flutter ديناميكيًا + إعادة كتابة نتائج package_info. */
    public boolean flutterDiscovery = true;
    public boolean flutterResultRewrite = true;
    /** تعطيل بوابات التحديث المكتشَفة بنيويًا (أسماء/توقيعات دلالية). */
    public boolean universalGate = true;
    /** مسح libapp.so وقت التشغيل (قراءة فقط) + خريطة المكتبات. */
    public boolean nativeScan = true;

    /* ── الخطّافات القائمة ── */
    public boolean neutralizeJson = true;
    public boolean blockStoreIntent = true;
    public String storeRedirectUrl = "https://www.genspark.ai/";
    public boolean blockWebViewStore = true;
    public boolean prefsHook = true;
    public boolean dialogWatch = false;
    public boolean playCore = true;
    public boolean nativeProbe = true;
    public boolean verbose = true;
    public boolean eventLog = true;
    public boolean adsBlock = true;
    public boolean blutterTrace = true;

    /* ── جسر الاشتراك (v2.0.1: تصحيح النجاح) ── */
    public boolean subscriptionBridge = false;
    public String subscriptionEndpoint = "";
    public String subscriptionToken = "";
    /** تفعيل نجاح محلي صريح (بدون خادم) — للاختبار/الإصلاح الناجح. */
    public boolean subscriptionForceActive = false;
    public String subscriptionPlan = "premium";
    public int subscriptionDays = 365;
    public String subscriptionUserId = "local";

    /** حدّ فشل مفتاح الإيقاف التلقائي. */
    public int killSwitchThreshold = 20;

    public String[] keys = {
        "forceUpgrade", "forceUpgradeD", "directUpgrade",
        "minAppVersionCode", "minAppRequireAppVersion", "requiresAppVersion"
    };

    /** حزم إضافية (لدعم نسخ أخرى/مُعاد تجديدها من نفس القاعدة دون إعادة بناء). */
    public String[] packages = {};

    private static volatile Config INSTANCE;
    private static volatile long lastCheck = 0L;
    private static volatile long lastMod   = 0L;
    private static final long RECHECK_MS = 3000L;

    public static Config get() {
        Config c = INSTANCE;
        long now = System.currentTimeMillis();
        if (c == null || now - lastCheck > RECHECK_MS) {
            synchronized (Config.class) {
                now = System.currentTimeMillis();
                if (INSTANCE == null || now - lastCheck > RECHECK_MS) {
                    lastCheck = now;
                    long m = newestMtime();
                    if (INSTANCE == null || m != lastMod) {
                        lastMod = m;
                        Config fresh = load();
                        INSTANCE = fresh;
                        Guard.setThreshold(fresh.killSwitchThreshold);
                        if (c != null) UxLog.i("Config: hot-reloaded (mtime changed)");
                    }
                    c = INSTANCE;
                }
            }
        }
        return c;
    }

    private static long newestMtime() {
        long m = 0L;
        for (String p : staticPaths()) {
            m = Math.max(m, mtime(p));
        }
        return m;
    }

    private static long mtime(String p) {
        try {
            File f = new File(p);
            if (f.exists() && f.canRead()) return f.lastModified();
        } catch (Throwable ignored) { }
        return 0L;
    }

    private static String[] staticPaths() {
        String pkg = EnvInfo.packageName();
        String alt = (pkg == null || pkg.length() == 0) ? "ai.mainfunc.genspark.pro" : pkg;
        return new String[]{
            "/data/local/tmp/genspark_updatekiller.json",
            "/sdcard/Download/genspark_updatekiller.json",
            "/sdcard/Android/data/" + alt + "/files/genspark_updatekiller.json"
        };
    }

    private static Config load() {
        Config c = new Config();
        for (String p : staticPaths()) {
            FileInputStream in = null;
            try {
                File f = new File(p);
                if (!f.exists() || !f.canRead()) continue;
                in = new FileInputStream(f);
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                JSONObject j = new JSONObject(new String(bo.toByteArray(), "UTF-8"));

                c.enabled             = j.optBoolean("enabled", c.enabled);
                c.spoofVersion        = j.optBoolean("spoofVersion", c.spoofVersion);
                c.spoofVersionCode    = j.optInt("spoofVersionCode", c.spoofVersionCode);
                c.spoofVersionName    = j.optString("spoofVersionName", c.spoofVersionName);
                c.classScan           = j.optBoolean("classScan", c.classScan);
                c.scanMaxNames        = j.optInt("scanMaxNames", c.scanMaxNames);
                c.lazyInstall         = j.optBoolean("lazyInstall", c.lazyInstall);
                c.pmDiscovery         = j.optBoolean("pmDiscovery", c.pmDiscovery);
                c.flutterDiscovery    = j.optBoolean("flutterDiscovery", c.flutterDiscovery);
                c.flutterResultRewrite = j.optBoolean("flutterResultRewrite", c.flutterResultRewrite);
                c.universalGate       = j.optBoolean("universalGate", c.universalGate);
                c.nativeScan          = j.optBoolean("nativeScan", c.nativeScan);
                c.neutralizeJson      = j.optBoolean("neutralizeJson", c.neutralizeJson);
                c.blockStoreIntent    = j.optBoolean("blockStoreIntent", c.blockStoreIntent);
                c.storeRedirectUrl    = j.optString("storeRedirectUrl", c.storeRedirectUrl);
                c.blockWebViewStore   = j.optBoolean("blockWebViewStore", c.blockWebViewStore);
                c.prefsHook           = j.optBoolean("prefsHook", c.prefsHook);
                c.dialogWatch         = j.optBoolean("dialogWatch", c.dialogWatch);
                c.playCore            = j.optBoolean("playCore", c.playCore);
                c.nativeProbe         = j.optBoolean("nativeProbe", c.nativeProbe);
                c.verbose             = j.optBoolean("verbose", c.verbose);
                c.eventLog            = j.optBoolean("eventLog", c.eventLog);
                c.adsBlock            = j.optBoolean("adsBlock", c.adsBlock);
                c.blutterTrace        = j.optBoolean("blutterTrace", c.blutterTrace);
                c.subscriptionBridge  = j.optBoolean("subscriptionBridge", c.subscriptionBridge);
                c.subscriptionEndpoint = j.optString("subscriptionEndpoint", c.subscriptionEndpoint);
                c.subscriptionToken   = j.optString("subscriptionToken", c.subscriptionToken);
                c.subscriptionForceActive = j.optBoolean("subscriptionForceActive", c.subscriptionForceActive);
                c.subscriptionPlan    = j.optString("subscriptionPlan", c.subscriptionPlan);
                c.subscriptionDays    = j.optInt("subscriptionDays", c.subscriptionDays);
                c.subscriptionUserId  = j.optString("subscriptionUserId", c.subscriptionUserId);
                c.killSwitchThreshold = j.optInt("killSwitchThreshold", c.killSwitchThreshold);

                JSONArray arr = j.optJSONArray("keys");
                if (arr != null && arr.length() > 0) {
                    String[] kk = new String[arr.length()];
                    for (int i = 0; i < arr.length(); i++) kk[i] = arr.optString(i, "");
                    c.keys = kk;
                }
                JSONArray pk = j.optJSONArray("packages");
                if (pk != null && pk.length() > 0) {
                    String[] pp = new String[pk.length()];
                    for (int i = 0; i < pk.length(); i++) pp[i] = pk.optString(i, "");
                    c.packages = pp;
                }
                break;
            } catch (Throwable ignored) {
            } finally {
                if (in != null) try { in.close(); } catch (Throwable ignored) { }
            }
        }
        return c;
    }

    /** مطابقة مرنة (يشمل المفاتيح المُسبَّقة مثل flutter.forceUpgrade). */
    public boolean hasKey(String k) {
        if (k == null) return false;
        for (String s : keys) {
            if (s == null || s.isEmpty()) continue;
            if (k.equals(s) || k.indexOf(s) >= 0) return true;
        }
        return false;
    }
}
