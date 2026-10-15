package com.apphook.log;

import java.io.File;
import java.io.FileWriter;

/**
 * كاتب التقارير: JSON إلى التخزين مع مسار احتياطي.
 * الأساسي /sdcard/SubscriptionAnalyzer/ (قد يفشل مع scoped storage) —
 * الاحتياطي دائماً يعمل: /data/data/&lt;pkg&gt;/cache (ملكية العملية نفسها).
 */
public final class ReportWriter {
    private ReportWriter() {}

    private static final long MIN_INTERVAL_MS = 30000;
    private static long lastWrite;

    public static void writeThrottled() {
        if (!Config.WRITE_REPORT) return;
        long now = System.currentTimeMillis();
        synchronized (ReportWriter.class) {
            if (now - lastWrite < MIN_INTERVAL_MS) return;
            lastWrite = now;
        }
        write();
    }

    public static void write() {
        if (!Config.WRITE_REPORT) return;
        String json;
        try {
            json = Analyzer.toJson();
        } catch (Throwable t) {
            SafeHook.logError("Report.build", t);
            return;
        }
        // 1) المسار الأساسي
        if (writeTo(new File(Config.REPORT_PATH), json)) return;
        // 2) الاحتياطي: cache العملية نفسها (يعمل دائماً بلا صلاحيات)
        try {
            String pkg = Config.runtimePackage;
            if (pkg != null && !pkg.equals("unknown")) {
                if (writeTo(new File("/data/data/" + pkg + "/cache/SubscriptionAnalyzer/"), json)) {
                    return;
                }
            }
        } catch (Throwable ignored) {}
        SafeHook.logSafe("Report", "all paths failed");
    }

    private static boolean writeTo(File dir, String json) {
        try {
            if (!dir.exists() && !dir.mkdirs()) return false;
            File out = new File(dir, "report_" + System.currentTimeMillis() + ".json");
            FileWriter w = new FileWriter(out);
            try {
                w.write(json);
            } finally {
                try {
                    w.close();
                } catch (Throwable ignored) {}
            }
            SafeHook.log("Report written: " + out.getAbsolutePath());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
