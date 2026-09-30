package com.genspark.updatekiller;

import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/* loaded from: classes.dex */
public final class UpdateEventLogger {
    private static final String FILE_NAME = "genspark_updatekiller_events.log";
    private static final Object LOCK = new Object();
    private static final long MAX_BYTES = 1048576;
    private static volatile String[] activePaths;

    private UpdateEventLogger() {
    }

    public static void log(String str, String str2) {
        try {
            if (Config.get().eventLog) {
                String str3 = timestamp() + " [" + str + "] " + str2 + "\n";
                synchronized (LOCK) {
                    boolean z = false;
                    for (String str4 : usablePaths()) {
                        try {
                            File file = new File(str4);
                            rotateIfNeeded(file);
                            FileOutputStream fileOutputStream = new FileOutputStream(file, true);
                            fileOutputStream.write(str3.getBytes("UTF-8"));
                            fileOutputStream.flush();
                            fileOutputStream.close();
                            z = true;
                        } catch (Throwable unused) {
                        }
                    }
                    if (!z) {
                        activePaths = null;
                    }
                }
                UxLog.d("EventLog: [" + str + "] " + str2);
            }
        } catch (Throwable unused2) {
        }
    }

    private static String timestamp() {
        try {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date());
        } catch (Throwable unused) {
            return String.valueOf(System.currentTimeMillis());
        }
    }

    private static String[] usablePaths() {
        String[] strArr = activePaths;
        if (strArr != null && strArr.length > 0) {
            return strArr;
        }
        ArrayList arrayList = new ArrayList();
        arrayList.add("/data/local/tmp/genspark_updatekiller_events.log");
        Object currentApplication = currentApplication();
        if (currentApplication != null) {
            Object invoke = Reflect.invoke(currentApplication, null, "getExternalFilesDir", new Object[]{null});
            if (invoke instanceof File) {
                arrayList.add(new File((File) invoke, FILE_NAME).getAbsolutePath());
            }
            Object invoke2 = Reflect.invoke(currentApplication, null, "getFilesDir", new Object[0]);
            if (invoke2 instanceof File) {
                arrayList.add(new File((File) invoke2, FILE_NAME).getAbsolutePath());
            }
        }
        arrayList.add("/sdcard/Download/genspark_updatekiller_events.log");
        activePaths = (String[]) arrayList.toArray(new String[0]);
        return activePaths;
    }

    private static Object currentApplication() {
        try {
            return Reflect.invoke(null, Reflect.findClass("android.app.ActivityThread", null), "currentApplication", new Object[0]);
        } catch (Throwable unused) {
            return null;
        }
    }

    private static void rotateIfNeeded(File file) {
        try {
            if (!file.exists() || file.length() <= MAX_BYTES) {
                return;
            }
            File file2 = new File(file.getParentFile(), FILE_NAME.replace(".log", ".old.log"));
            if (file2.exists()) {
                file2.delete();
            }
            file.renameTo(file2);
        } catch (Throwable unused) {
        }
    }
}
