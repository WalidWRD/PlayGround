package com.loktv.hook;

/**
 * Central metadata for the module v3.0.0.
 * DESCRIPTION_* are intentionally SHORT: the module manager shows them in a
 * small box. The full feature list lives in the runtime log banner.
 * Update VERSION/VERSION_CODE/BUILD_TAG on each release so the manager,
 * the log banner and docs/ANALYSIS stay in sync.
 */
public final class ModuleInfo {

    public static final String MODULE_ID      = "LOKTV-HOOK-PRO";
    public static final String MODULE_NAME    = "LOKTV Hook Pro";
    public static final String VERSION        = "3.2.1";
    public static final int    VERSION_CODE   = 321;
    public static final String BUILD_TAG      = "2026.10.08-r5";
    public static final String AUTHOR         = "LOKTV Hook Pro Project";

    /** Module supported Android range (mirrors manifest min/targetSdk). */
    public static final String ANDROID_MIN    = "5.0 (API 21)";
    public static final String ANDROID_TARGET = "14 (API 34)";

    /** Primary target + aliases (multi-version tolerant matching). */
    public static final String TARGET_PACKAGE = "com.novan.morpha";
    public static final String[] TARGET_ALIASES = {
            "com.novan.morpha",
            "com.novan",
            "morpha",
            "novan"
    };

    /**
     * v3.0.0: dex-signature classes. When the package name is renamed
     * (clone builds), detection falls back to these — any hit = target.
     */
    public static final String[] SIGNATURE_CLASSES = {
            "com.novan.morpha.UserStatus",
            "com.novan.morpha.AntiVPN",
            "com.novan.morpha.VipItem",
            "com.novan.morpha.LoadConfig"
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
            "F11 Packed/protected APK + package-agnostic dex-signature detection",
            "F12 Crash-guard: isolated steps + full-stack hook-origin check",
            "F13 File + logcat runtime log + proof toast + per-feature counters",
            "F14 Hot configuration (key=value, no re-patch, validated rotation)",
            "F15 Ads/splash/banner/reward/interstitial block",
            "F16 Analytics/tracker disabler (perf + privacy)",
            "F17 Forced-update/notice dialog suppressor",
            "F18 VIP purchase UI hider (BuyVip/VipCard/pay dialogs)",
            "F19 VipItem unlock hardening (boolean+level+expiry)",
            "F20 License/store-redirect bypass (anti Play-Store bounce)",
            "F21 Legacy installer invoker (void isVip/isDisable/... are CALLED, not neutered)"
    };

    /** Short description for the module manager (Arabic, concise). */
    public static final String DESCRIPTION_AR =
            "LOKTV Hook Pro v3.2.0 | هوك LOKTV: VIP + منع التحديث + حجب إعلانات/تتبع. "
          + "يعمل على أي إصدار/حزمة عبر NPatch و LSPatch و HKP بدون روت. أندرويد 5.0–14.";

    /** Short description for the module manager (English, concise). */
    public static final String DESCRIPTION_EN =
            "LOKTV Hook Pro v3.2.0 | LOKTV hook: VIP + no forced update + ads/trackers off. "
          + "Any version/package via NPatch, LSPatch, HKP, no root. Android 5.0-14.";

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
        sb.append("  module android: ").append(ANDROID_MIN)
          .append(" -> ").append(ANDROID_TARGET).append("\n");
        sb.append("  loader: ").append(FRAMEWORKS).append("\n");
        sb.append("  features:\n");
        for (String f : FEATURES) sb.append("    - ").append(f).append("\n");
        sb.append("==============================================================");
        return sb.toString();
    }

    private ModuleInfo() {}
}
