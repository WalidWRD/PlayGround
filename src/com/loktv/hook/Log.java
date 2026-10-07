package com.loktv.hook;

import android.content.Context;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Dual-channel logger: logcat (via XposedBridge.log) + rolling file inside the
 * target app private storage. All failures are swallowed: logging must never
 * be a crash source.
 */
public final class Log {

    private static final String TAG = "LOKTV-HOOK-PRO";
    private static final long MAX_BYTES = 256 * 1024L;

    private static String sPath;
    private static boolean sReady;

    public static void init(Context ctx, String pkg) {
        try {
            File dir = null;
            if (ctx != null) dir = ctx.getFilesDir();
            if (dir == null) dir = new File("/sdcard/Android/data/" + pkg + "/files");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, "loktv_hook.log");
            if (f.exists() && f.length() > MAX_BYTES) {
                File old = new File(dir, "loktv_hook.old.log");
                if (old.exists()) old.delete();
                f.renameTo(old);
            }
            sPath = f.getAbsolutePath();
            sReady = true;
            write("---- session start ----");
        } catch (Throwable ignored) {
            sReady = false;
        }
    }

    public static String path() { return sPath; }

    public static void banner() { raw(ModuleInfo.banner()); }

    public static void i(String msg) { raw("I/" + msg); }
    public static void w(String msg) { raw("W/" + msg); }
    public static void e(String msg) { raw("E/" + msg); }

    public static void e(String msg, Throwable t) {
        raw("E/" + msg + " :: " + describe(t));
        if (t != null) {
            try {
                java.io.StringWriter sw = new java.io.StringWriter();
                t.printStackTrace(new PrintWriter(sw));
                raw(sw.toString());
            } catch (Throwable ignored) {}
        }
    }

    public static String describe(Throwable t) {
        if (t == null) return "null";
        try {
            return t.getClass().getName() + ": " + t.getMessage();
        } catch (Throwable x) {
            return "unknown-throwable";
        }
    }

    private static void raw(String msg) {
        try {
            de.robv.android.xposed.XposedBridge.log(TAG + " | " + msg);
        } catch (Throwable ignored) {}
        try {
            android.util.Log.println(android.util.Log.INFO, TAG, msg);
        } catch (Throwable ignored) {}
        write(msg);
    }

    private static void write(String msg) {
        if (!sReady || sPath == null) return;
        FileWriter fw = null;
        try {
            String ts = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(new Date());
            fw = new FileWriter(sPath, true);
            fw.write(ts + "  " + msg + "\n");
            fw.flush();
        } catch (Throwable ignored) {
        } finally {
            try { if (fw != null) fw.close(); } catch (Throwable ignored) {}
        }
    }

    private Log() {}
}
