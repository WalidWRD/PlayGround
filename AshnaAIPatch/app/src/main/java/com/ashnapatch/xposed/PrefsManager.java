package com.ashnapatch.xposed;

import com.ashnapatch.xposed.util.LogUtil;

import de.robv.android.xposed.XSharedPreferences;

/**
 * مفاتيح الإعدادات الحية — تُقرأ من شاشة التحكم (SettingsActivity) دون إعادة بناء.
 * يعمل مع LSPosed/LSPatch عبر XSharedPreferences، وعند تعذّر القراءة
 * (صلاحيات/أول تشغيل) يعود لقيم Config الافتراضية — لا كراش أبداً.
 */
public final class PrefsManager {
    private PrefsManager() {}

    public static final String PREFS_FILE = "ashna_patch_prefs";
    public static final String KEY_UPDATE = "update_block";
    public static final String KEY_WARNING = "warning_filter";
    public static final String KEY_AD = "ad_block";
    public static final String KEY_JS = "js_hide";
    /** قوائم المستخدم المخصصة (نص خام يفصل بين البنود بفاصلة أو سطر جديد) — حد 100 بند لكل قائمة. */
    public static final String KEY_CUSTOM_HOSTS = "custom_ad_hosts";
    public static final String KEY_CUSTOM_WARN = "custom_warn_keywords";
    /** التشخيص الشبكي: سجل قراءة فقط (مغلق افتراضياً) — لا يعترض ولا يعدّل أي طلب. */
    public static final String KEY_NETDIAG = "net_diag";
    /**
     * تذكير الاشتراك المحلي فقط: اسم الخطة وتاريخ انتهائها بصيغة YYYY-MM-DD.
     * يُدخله المستخدم يدوياً ويُحفظ على جهازه فقط — الموديول لا يقرأ
     * اشتراك الخادم ولا يعدّله ولا يعترض أي مسار دفع/مصادقة.
     */
    public static final String KEY_SUB_PLAN = "sub_plan_name";
    public static final String KEY_SUB_EXPIRY = "sub_expiry_date";

    private static volatile XSharedPreferences xPrefs;
    private static volatile boolean triedInit;

    private static synchronized void ensureInit() {
        if (triedInit) return;
        triedInit = true;
        // جرّب اسم الحزمة الأساسي ثم لاحقة debug (لنسخ التجربة).
        String[] pkgs = new String[]{"com.ashnapatch.xposed", "com.ashnapatch.xposed.debug"};
        for (String pkg : pkgs) {
            try {
                XSharedPreferences p = new XSharedPreferences(pkg, PREFS_FILE);
                try {
                    p.makeWorldReadable();
                } catch (Throwable ignored) {
                }
                // اعتبره صالحاً فقط إن وُجد ملف فعلي.
                try {
                    if (p.getFile() != null && p.getFile().exists()) {
                        xPrefs = p;
                        LogUtil.info("Prefs loaded from: " + pkg);
                        return;
                    }
                } catch (Throwable ignored) {
                }
            } catch (Throwable ignored) {
            }
        }
        LogUtil.info("Prefs file not found yet — using Config defaults.");
    }

    private static void refreshIfNeeded() {
        try {
            if (xPrefs == null) return;
            try {
                if (xPrefs.hasFileChanged()) xPrefs.reload();
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }
    }

    private static boolean get(String key, boolean fallback) {
        try {
            ensureInit();
            refreshIfNeeded();
            if (xPrefs == null) return fallback;
            try {
                return xPrefs.getBoolean(key, fallback);
            } catch (Throwable t) {
                return fallback;
            }
        } catch (Throwable t) {
            return fallback;
        }
    }

    public static boolean getUpdateBlock() {
        return get(KEY_UPDATE, Config.ENABLE_UPDATE_BLOCK);
    }

    public static boolean getWarningFilter() {
        return get(KEY_WARNING, Config.ENABLE_WARNING_FILTER);
    }

    public static boolean getAdBlock() {
        return get(KEY_AD, Config.ENABLE_AD_BLOCK);
    }

    public static boolean getJsHide() {
        return get(KEY_JS, Config.ENABLE_JS_HIDE);
    }

    /** يُستدعى عند إقلاع الحزمة لإحماء القراءة مبكراً (آمن تماماً). */
    public static void warmup() {
        try {
            ensureInit();
            refreshIfNeeded();
        } catch (Throwable ignored) {
        }
    }

    private static String getString(String key, String fallback) {
        try {
            ensureInit();
            refreshIfNeeded();
            if (xPrefs == null) return fallback;
            try {
                return xPrefs.getString(key, fallback);
            } catch (Throwable t) {
                return fallback;
            }
        } catch (Throwable t) {
            return fallback;
        }
    }

    /** يحوّل النص الخام إلى بنود صغيرة نظيفة (حد 100) — لا يرمي أبداً. */
    static java.util.Set<String> parseList(String raw) {
        java.util.Set<String> out = new java.util.HashSet<>();
        try {
            if (raw == null) return out;
            String[] parts = raw.split("[,;\n]+");
            for (String p : parts) {
                try {
                    String s = p.trim().toLowerCase();
                    if (s.isEmpty() || s.length() > 120) continue;
                    out.add(s);
                    if (out.size() >= 100) break;
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    public static java.util.Set<String> getCustomHosts() {
        try {
            return parseList(getString(KEY_CUSTOM_HOSTS, ""));
        } catch (Throwable t) {
            return new java.util.HashSet<>();
        }
    }

    public static java.util.Set<String> getCustomWarnKeywords() {
        try {
            return parseList(getString(KEY_CUSTOM_WARN, ""));
        } catch (Throwable t) {
            return new java.util.HashSet<>();
        }
    }

    /** هل الرابط يطابق قائمة المستخدم المخصصة؟ (إضافة فوق Config — لا تحل محلها). */
    public static boolean isCustomAdHost(String url) {
        try {
            if (url == null || url.isEmpty()) return false;
            String lower = url.toLowerCase();
            java.util.Set<String> hosts = getCustomHosts();
            if (hosts.isEmpty()) return false;
            for (String h : hosts) {
                if (h != null && !h.isEmpty() && lower.contains(h)) return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /** هل النص يطابق كلمات التحذير المخصصة؟ (القائمة البيضاء تبقى أقوى دائماً). */
    public static boolean matchesCustomWarning(String text) {
        try {
            if (text == null || text.isEmpty()) return false;
            String lower = text.toLowerCase();
            java.util.Set<String> kws = getCustomWarnKeywords();
            if (kws.isEmpty()) return false;
            for (String k : kws) {
                if (k != null && !k.isEmpty() && lower.contains(k)) return true;
            }
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    /** سطر تشخيص واحد لسجل الإقلاع: الحالات + أحجام القوائم المخصصة. */
    public static String stateLine() {
        try {
            return "Toggles: update=" + getUpdateBlock()
                    + " warning=" + getWarningFilter()
                    + " ad=" + getAdBlock()
                    + " js=" + getJsHide()
                    + " netdiag=" + getNetDiag()
                    + " | custom hosts=" + getCustomHosts().size()
                    + " warn=" + getCustomWarnKeywords().size();
        } catch (Throwable t) {
            return "Toggles: (unavailable)";
        }
    }

    public static boolean getNetDiag() {
        return get(KEY_NETDIAG, false);
    }

    public static String getSubPlan() {
        try {
            String s = getString(KEY_SUB_PLAN, "");
            return s == null ? "" : s;
        } catch (Throwable t) {
            return "";
        }
    }

    public static String getSubExpiry() {
        try {
            String s = getString(KEY_SUB_EXPIRY, "");
            return s == null ? "" : s;
        } catch (Throwable t) {
            return "";
        }
    }

    /**
     * نص حالة الاشتراك المحلي: اسم الخطة + الأيام المتبقية من التاريخ المدخل.
     * حساب محلي بحت — لا يتصل بأي خادم ولا يعكس حالة الخادم الفعلية.
     */
    public static String subStatusLine() {
        try {
            String plan = getSubPlan().trim();
            String exp = getSubExpiry().trim();
            if (plan.isEmpty() && exp.isEmpty()) {
                return "لا يوجد تذكير محفوظ — أدخل خطتك وتاريخ انتهائها للتذكير فقط.";
            }
            if (exp.isEmpty()) return "الخطة: " + plan + " (بلا تاريخ انتهاء).";
            long days = daysUntil(exp);
            if (days == Long.MIN_VALUE) {
                return "الخطة: " + plan + " — صيغة التاريخ غير صالحة (استخدم YYYY-MM-DD).";
            }
            if (days < 0) {
                return "الخطة: " + plan + " — انتهى التاريخ المدخل منذ " + (-days) + " يوم (تذكير محلي).";
            }
            if (days == 0) return "الخطة: " + plan + " — ينتهي التاريخ المدخل اليوم (تذكير محلي).";
            return "الخطة: " + plan + " — المتبقي على التاريخ المدخل: " + days + " يوم (تذكير محلي).";
        } catch (Throwable t) {
            return "تعذّر حساب الحالة المحلية.";
        }
    }

    /** الأيام حتى تاريخ YYYY-MM-DD، أو Long.MIN_VALUE عند فشل التحليل. */
    static long daysUntil(String yyyyMmDd) {
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd");
            f.setLenient(false);
            java.util.Date target = f.parse(yyyyMmDd);
            if (target == null) return Long.MIN_VALUE;
            java.util.Calendar c = java.util.Calendar.getInstance();
            c.set(java.util.Calendar.HOUR_OF_DAY, 0);
            c.set(java.util.Calendar.MINUTE, 0);
            c.set(java.util.Calendar.SECOND, 0);
            c.set(java.util.Calendar.MILLISECOND, 0);
            java.util.Calendar t = java.util.Calendar.getInstance();
            t.setTime(target);
            t.set(java.util.Calendar.HOUR_OF_DAY, 0);
            t.set(java.util.Calendar.MINUTE, 0);
            t.set(java.util.Calendar.SECOND, 0);
            t.set(java.util.Calendar.MILLISECOND, 0);
            long diff = t.getTimeInMillis() - c.getTimeInMillis();
            return diff / 86400000L;
        } catch (Throwable t) {
            return Long.MIN_VALUE;
        }
    }
}
