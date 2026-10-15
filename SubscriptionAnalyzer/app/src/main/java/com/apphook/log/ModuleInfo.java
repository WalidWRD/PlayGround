package com.apphook.log;

import android.util.Log;

import io.github.libxposed.api.XposedInterface;

/**
 * هوية الموديول + الوصف المدمج — مصدر واحد للحقيقة.
 */
public final class ModuleInfo {
    private ModuleInfo() {}

    public static final String NAME = "SubscriptionAnalyzer";
    public static final String PACKAGE = "com.apphook.log";
    public static final String VERSION_NAME = "6.0.0";
    public static final int VERSION_CODE = 600;

    public static final int MIN_SDK = 26;
    public static final int TARGET_SDK = 35;
    public static final int LIB_MIN_API = 101;
    public static final int LIB_TARGET_API = 102;

    /** الوصف المدمج: مختصر — مدير الموديولات + سجل التشغيل */
    public static String describe() {
        return NAME + " v" + VERSION_NAME + " [" + Config.modeName() + "]"
                + " | billing+webview+flutter+network measurement"
                + " | optional patch | crash-proof";
    }

    public static void logStartup(XposedInterface fw) {
        try {
            fw.log(Log.INFO, "SA", describe());
            fw.log(Log.INFO, "SA",
                    "[Features] F000-local F001-plan F002-balance F003-billing "
                    + "F004-channel F005-json F006-prefs F007-web");
        } catch (Throwable ignored) {}
    }
}
