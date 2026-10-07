package com.loktv.hook;

/**
 * Central metadata for the module v2.1.0.
 * The embedded description below is what the module manager (LSPatch / NPatch / HKP /
 * LSPosed) reads from AndroidManifest (xposeddescription) and what is printed to the
 * runtime log at every launch. Update VERSION/VERSION_CODE/BUILD_TAG on each release
 * so the manager, the log banner and docs/ANALYSIS stay in sync.
 */
public final class ModuleInfo {

    public static final String MODULE_ID      = "LOKTV-HOOK-PRO";
    public static final String MODULE_NAME    = "LOKTV Hook Pro";
    public static final String VERSION        = "2.1.1";
    public static final int    VERSION_CODE   = 211;
    public static final String BUILD_TAG      = "2026.10.07-r3";
    public static final String AUTHOR         = "LOKTV Hook Pro Project";

    /** Primary target + aliases (multi-version tolerant matching). */
    public static final String TARGET_PACKAGE = "com.novan.morpha";
    public static final String[] TARGET_ALIASES = {
            "com.novan.morpha",
            "com.novan",
            "morpha",
            "novan"
    };

    /** Supported loader frameworks. */
    public static final String FRAMEWORKS =
            "LSPatch / NPatch / HKP-Patch / LSPosed / EdXposed  (no root required)";

    /** Compatible app version range (inclusive). */
    public static final String MIN_APP_VERSION = "1.0.0";
    public static final String MAX_APP_VERSION = "unlimited (reflection-based, version agnostic)";

    public static final String[] FEATURES = new String[]{
            "F01 VIP / Premium unlock (reflection boolean + level/expiry hardening)",
            "F02 Skip forced update (isSkipUpdate=true, isForceUpdate/hasUpdate=false)",
            "F03 Valid collection / library unlock (isValidCollection)",
            "F04 Video data channel unlock (isVideoDataEnabled)",
            "F05 Popup & dialog suppressor (DisableSomePopup, no-op engine)",
            "F06 Mod/root/emulator/signature neutralizer (boolean->false, string->empty)",
            "F07 Anti-VPN / proxy gate bypass (class + method-name heuristic)",
            "F08 Floating view (overlay ads) disabler + obfuscation fallback",
            "F09 Screen rotation controller (off / portrait / landscape)",
            "F10 Dex-wide scanner (boolean/int/long, survives renaming & obfuscation)",
            "F11 Packed / protected APK support (attachBaseContext+onCreate+retry)",
            "F12 Crash-guard: isolated steps + full-stack hook-origin check",
            "F13 File + logcat runtime log with applied/failed counters",
            "F14 Hot configuration (key=value, no re-patch, validated rotation)",
            "F15 Ads/splash/banner/reward/interstitial block",
            "F16 Analytics/tracker disabler (perf + privacy)",
            "F17 Forced-update/notice dialog suppressor"
    };

    /** Short description embedded in the module manager (Arabic, updated). */
    public static final String DESCRIPTION_AR =
            "LOKTV Hook Pro v2.1.1 | موديول هوك احترافي لتطبيق LOKTV (com.novan.morpha). "
          + "يفتح VIP (مع تقوية المستوى وتاريخ الانتهاء)، يمنع التحديث الإجباري "
          + "(isSkipUpdate=true و isForceUpdate/hasUpdate=false)، يفعّل المجموعة والفيديو، "
          + "يزيل النوافذ والإعلانات العائمة والبانر والمكافآت، يحجب التحليلات والتتبع، "
          + "يتجاوز كشف VPN/Proxy والتعديل والروت والمحاكي والبصمة. "
          + "يعمل بالانعكاس Reflection + فاحص dex شامل (boolean/int/long) فيدعم الإصدارات "
          + "الأعلى والأقل والتطبيقات المضغوطة/المحمية عبر NPatch و LSPatch و HKP بدون روت. "
          + "حماية كاملة من الكراشات (خطوات معزولة + فحص مصدر الاستثناء) + سجل تشغيل "
          + "+ إعدادات ساخنة قابلة للتطوير والصيانة.";

    /** Short description embedded in the module manager (English, updated). */
    public static final String DESCRIPTION_EN =
            "LOKTV Hook Pro v2.1.1 | Professional hook module for LOKTV (com.novan.morpha). "
          + "Unlocks VIP (boolean + level/expiry hardening), blocks forced updates "
          + "(isSkipUpdate=true, isForceUpdate/hasUpdate=false), enables collection & video, "
          + "removes popups/floating/banner/reward ads, disables trackers, bypasses "
          + "VPN/proxy/tamper/root/emulator/signature checks. "
          + "Reflection + dex-wide scanner (boolean/int/long) => works on higher & lower "
          + "versions and packed/protected APKs via NPatch, LSPatch and HKP with no root. "
          + "Full crash-guard (isolated steps + origin check), runtime log, hot config, "
          + "maintainable architecture (see docs/ANALYSIS-v2.1.0.md).";

    /** Compact one-liner used in the runtime log header. */
    public static String oneLiner() {
        return MODULE_NAME + " v" + VERSION + " (build " + BUILD_TAG + ") -> " + TARGET_PACKAGE
             + " | " + FEATURES.length + " features | " + FRAMEWORKS;
    }

    /** Multi-line banner written at startup. */
    public static String banner() {
        StringBuilder sb = new StringBuilder();
        sb.append("==============================================================\n");
        sb.append("  ").append(MODULE_NAME).append(" v").append(VERSION)
          .append("  [").append(MODULE_ID).append("]\n");
        sb.append("  build : ").append(BUILD_TAG).append("\n");
        sb.append("  target: ").append(TARGET_PACKAGE).append("\n");
        sb.append("  min app version: ").append(MIN_APP_VERSION)
          .append("   max: ").append(MAX_APP_VERSION).append("\n");
        sb.append("  loader: ").append(FRAMEWORKS).append("\n");
        sb.append("  features:\n");
        for (String f : FEATURES) sb.append("    - ").append(f).append("\n");
        sb.append("==============================================================");
        return sb.toString();
    }

    private ModuleInfo() {}
}
