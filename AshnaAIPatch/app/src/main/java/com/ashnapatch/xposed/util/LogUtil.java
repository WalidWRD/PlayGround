package com.ashnapatch.xposed.util;

import android.util.Log;

import de.robv.android.xposed.XposedBridge;

/**
 * تسجيل آمن: لا يرمي استثناء أبداً (حماية من الكراشات).
 */
public final class LogUtil {
    private LogUtil() {}

    public static final String TAG = "AshnaAIPatch";

    public static void info(String msg) {
        try {
            XposedBridge.log("[" + TAG + "] " + msg);
        } catch (Throwable t) {
            try {
                Log.i(TAG, msg);
            } catch (Throwable ignored) {
            }
        }
    }

    public static void warn(String msg, Throwable t) {
        try {
            String extra = (t == null) ? "" : (" | " + t.getClass().getSimpleName()
                    + ": " + t.getMessage());
            XposedBridge.log("[" + TAG + "] WARN: " + msg + extra);
        } catch (Throwable ignored) {
        }
    }
}
