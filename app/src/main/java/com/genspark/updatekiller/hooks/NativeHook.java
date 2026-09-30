package com.genspark.updatekiller.hooks;

import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.UxLog;
import java.io.File;
import java.io.FileInputStream;

/**
 * v2.0.2: no more 50MB String allocation (OOM risk) — chunked scan;
 * resolve libapp.so via ApplicationInfo.nativeLibraryDir + multi-user
 * /data/user paths instead of hard-coded /data/data only.
 */
public final class NativeHook {
    private NativeHook() {
    }

    public static int install(ClassLoader classLoader) {
        if (!Config.get().nativeProbe) {
            UxLog.i("NativeHook: disabled");
            return 0;
        }
        try {
            Class<?> cls = Reflect.findClass("java.lang.Runtime", classLoader);
            if (cls != null) {
                try {
                    cls.getDeclaredMethod("loadLibrary", String.class);
                } catch (Throwable t) {
                    Guard.record("NativeHook.loadLibrary", t);
                }
            }
        } catch (Throwable th) {
            Guard.record("NativeHook.loadLibrary", th);
        }
        int scanned = 0;
        try {
            for (String path : candidateLibs()) {
                try {
                    File file = new File(path);
                    if (file.exists() && file.canRead()) {
                        scanned++;
                        scanForcedUpgrade(file);
                    }
                } catch (Throwable th) {
                    Guard.record("NativeHook.scan", th);
                }
            }
        } catch (Throwable th) {
            Guard.record("NativeHook.scan", th);
        }
        UxLog.i("NativeHook: " + scanned + " lib scanned");
        return scanned;
    }

    private static String[] candidateLibs() {
        String[] pkgs = {"ai.mainfunc.genspark.pro", "ai.mainfunc.genspark"};
        String[] users = {"", "/data/user/0", "/data/user_de/0"};
        java.util.ArrayList<String> out = new java.util.ArrayList<>();
        // 1) live app dir via ActivityThread (works under LSPatch, any user id)
        try {
            Object app = Reflect.invoke(null,
                    Reflect.findClass("android.app.ActivityThread", null),
                    "currentApplication", new Object[0]);
            if (app != null) {
                Object ai = Reflect.invoke(app, null, "getApplicationInfo", new Object[0]);
                if (ai instanceof android.content.pm.ApplicationInfo) {
                    String nativeDir = ((android.content.pm.ApplicationInfo) ai).nativeLibraryDir;
                    if (nativeDir != null) {
                        out.add(nativeDir + "/libapp.so");
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        for (String u : users) {
            String base = u.isEmpty() ? "/data/data" : u;
            for (String p : pkgs) {
                out.add(base + "/" + p + "/lib/arm64-v8a/libapp.so");
                out.add(base + "/" + p + "/lib/armeabi-v7a/libapp.so");
            }
        }
        return out.toArray(new String[0]);
    }

    private static void scanForcedUpgrade(File file) {
        FileInputStream in = null;
        try {
            in = new FileInputStream(file);
            byte[] buf = new byte[64 * 1024];
            byte[] needle = "forceUpgrade".getBytes("UTF-8");
            int read;
            int match = 0;
            long total = 0;
            // cap scan at 50MB like before, but streaming
            while (total < 52428800L && (read = in.read(buf)) != -1) {
                total += read;
                for (int i = 0; i < read; i++) {
                    if (buf[i] == needle[match]) {
                        match++;
                        if (match == needle.length) {
                            UxLog.i("NativeHook: found 'forceUpgrade' in " + file.getAbsolutePath());
                            match = 0;
                        }
                    } else {
                        match = (buf[i] == needle[0]) ? 1 : 0;
                    }
                }
            }
        } catch (Throwable th) {
            Guard.record("NativeHook.scanFU", th);
        } finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Throwable ignored) {
            }
        }
    }
}
