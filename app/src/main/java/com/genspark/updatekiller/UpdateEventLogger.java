package com.genspark.updatekiller;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * جديد 1.3.0 — مسجّل أحداث التحديث: يكتب سطرًا في ملف log عند كل دليل على
 * ظهور حوار/بوابة تحديث مع وقت الظهور المحلي بدقة ميلي ثانية.
 *
 * ما يُسجَّل (الاستدعاءات تأتي من الخطّافات):
 *   [channel]  نداء قناة Flutter مرتبط بالتحديث (openStoreListing / checkUpdate / updateNow …)
 *   [dialog]   أي حوار أصلي (Java) يظهر — مع تمييز ما يشبه حوارات التحديث
 *   [intent]   نية فتح متجر Play (زر «حدّث الآن») المحوَّلة
 *   [http]     ردّ إعداد التحديث من الخادم (forceUpgrade/minAppVersionCode…) قبل تعطيله
 *   [session]  بدء الموديول وعدد الخطّافات
 *
 * المسارات (تُجرَّب جميعها، ويُعاد الكشف تلقائيًا عند تعذّر الكتابة):
 *   1) /data/local/tmp/genspark_updatekiller_events.log   (روت/محاكي/adb)
 *   2) /sdcard/Android/data/<حزمة التطبيق>/files/…         (خارجي خاص بالتطبيق — بلا أذونات)
 *   3) /data/data/<حزمة التطبيق>/files/…                   (داخلي — يُقرأ بالروت أو adb run-as)
 *   4) /sdcard/Download/…                                  (أجهزة قديمة)
 * يُجلب Context انعكاسيًا من ActivityThread.currentApplication() — يصمد أمام
 * التشويش ويُعاد تجرّبه تلقائيًا إن لم يكن التطبيق قد أُهيّئ بعد.
 * تقلّص تلقائي: عند تجاوز 1MB يُنقل الملف إلى *.old.log ويبدأ ملف جديد.
 */
public final class UpdateEventLogger {

    private static final String FILE_NAME = "genspark_updatekiller_events.log";
    private static final long MAX_BYTES = 1024L * 1024L;

    private static final Object LOCK = new Object();
    private static volatile String[] activePaths = null;

    private UpdateEventLogger() { }

    /** يُسجّل حدثًا — لا يرمي أبدًا ولا يعطّل الخطّاف المستدعي. */
    public static void log(String source, String detail) {
        try {
            if (!Config.get().eventLog) return;
            String line = timestamp() + " [" + source + "] " + detail + "\n";
            synchronized (LOCK) {
                boolean wrote = false;
                for (String p : usablePaths()) {
                    try {
                        File f = new File(p);
                        rotateIfNeeded(f);
                        FileOutputStream out = new FileOutputStream(f, true);
                        out.write(line.getBytes("UTF-8"));
                        out.flush();
                        out.close();
                        wrote = true;
                    } catch (Throwable t) { /* المسار غير متاح — نتابع إلى التالي */ }
                }
                if (!wrote) activePaths = null; // أعد الكشف عند الحدث التالي (قد يصبح Context متاحًا)
            }
            UxLog.d("EventLog: [" + source + "] " + detail);
        } catch (Throwable ignored) { }
    }

    private static String timestamp() {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", java.util.Locale.US).format(new Date());
        } catch (Throwable t) { return String.valueOf(System.currentTimeMillis()); }
    }

    private static String[] usablePaths() {
        String[] a = activePaths;
        if (a != null && a.length > 0) return a;

        List<String> ok = new ArrayList<>();
        // 1) بيئات روت / adb
        ok.add("/data/local/tmp/" + FILE_NAME);
        // 2+3) مجلدات التطبيق عبر Context (انعكاسيًا)
        Object ctx = currentApplication();
        if (ctx != null) {
            Object ext = Reflect.invoke(ctx, null, "getExternalFilesDir", new Object[]{null});
            if (ext instanceof File) ok.add(new File((File) ext, FILE_NAME).getAbsolutePath());
            Object in = Reflect.invoke(ctx, null, "getFilesDir", new Object[0]);
            if (in instanceof File) ok.add(new File((File) in, FILE_NAME).getAbsolutePath());
        }
        // 4) التحميلات (أجهزة قديمة)
        ok.add("/sdcard/Download/" + FILE_NAME);

        activePaths = ok.toArray(new String[0]);
        return activePaths;
    }

    private static Object currentApplication() {
        try {
            Class<?> at = Reflect.findClass("android.app.ActivityThread", null);
            return Reflect.invoke(null, at, "currentApplication", new Object[0]);
        } catch (Throwable t) { return null; }
    }

    private static void rotateIfNeeded(File f) {
        try {
            if (f.exists() && f.length() > MAX_BYTES) {
                File old = new File(f.getParentFile(), FILE_NAME.replace(".log", ".old.log"));
                if (old.exists()) old.delete();
                f.renameTo(old);
            }
        } catch (Throwable ignored) { }
    }
}
