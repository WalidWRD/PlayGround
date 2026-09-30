package com.genspark.updatekiller;

/** معلومات الموديول — نقطة واحدة لكل ما يظهر للمستخدم. */
public final class BuildInfo {

    public static final String MODULE_NAME  = "GenSubs UpdateKiller";
    public static final String VERSION_NAME = "2.0.1";
    public static final int    VERSION_CODE = 201;
    public static final String TARGET_APP   = "Genspark";
    public static final String AUTHOR       = "GenSubs Project";

    /** الحزم المستهدفة الافتراضية (تُوسَّع من الإعدادات: مفتاح packages). */
    public static final String[] PACKAGE_NAMES = {
        "ai.mainfunc.genspark.pro",
        "ai.mainfunc.genspark"
    };

    public static String describe() {
        return MODULE_NAME + " v" + VERSION_NAME + " (" + VERSION_CODE + ")\n"
             + "Target app: " + TARGET_APP + " — " + PACKAGE_NAMES[0] + " (+ " + PACKAGE_NAMES[1] + ")\n"
             + "Engine: lpparam.classLoader + DEX class index + signature/reflection matching\n"
             + "        (no hard-coded class or method names, all overloads hooked)\n"
             + "Features: forced-update bypass, instant/optional update dialog suppression,\n"
             + "          dynamic PackageManager/version spoofing, dynamic Flutter channel discovery,\n"
             + "          package_info result rewrite, JSON decision neutralization, store-intent +\n"
             + "          in-app WebView store blocking, pref-cache neutralization, Play Core\n"
             + "          neutralization, universal semantic update-gate neutralizer, ad blocker,\n"
             + "          native libapp.so/maps probe (read-only), update-event file logging,\n"
             + "          self-test JSON report, hot-reloadable config, multi-pass deferred install\n"
             + "          (lazy retry) for compressed/protected/obfuscated apps.\n"
             + "Safety: Guard + auto Kill-Switch (any failure is swallowed, never crashes the app).\n"
             + "Env: LSPatch / NPatch / HKP-Patch / LSPosed — no root required.";
    }

    private BuildInfo() { }
}
