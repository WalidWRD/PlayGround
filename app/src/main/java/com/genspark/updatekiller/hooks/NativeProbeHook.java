package com.genspark.updatekiller.hooks;

import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.EnvInfo;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.UpdateEventLogger;
import com.genspark.updatekiller.UxLog;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * جديد 2.0.0 — «مسح الطبقة الأصلية وقت التشغيل» (قراءة فقط).
 *
 * يقرأ /proc/self/maps لمعرفة مكتبات التطبيق الفعلية على الجهاز (لا افتراض لمسار)،
 * ثم لكل من libapp.so وlibflutter.so يحسب:
 *   • الحجم الحقيقي
 *   • SHA-256 (للتحقق من أنك تتعامل مع نفس الـAOT snapshot)
 *   • عدد مرات ظهور «نصوص قرار التحديث» المعروفة داخل الملف
 *     (forceUpgrade، minAppVersionCode، SasUpgradeGateSheet، /api/config/new_feature/dialog، …)
 *
 * الناتج يُكتب في ملف الأحداث وفي logcat — فيصبح لديك دليل ميداني بدل التحليل اليدوي.
 * المسح قراءة فقط ولا يعدّل أي ملف، ولا يرفع شيئًا للشبكة.
 */
public final class NativeProbeHook {

    private static final String[] LIB_NAMES = { "libapp.so", "libflutter.so" };

    private static final String[] MARKERS = {
        "forceUpgrade", "forceUpgradeD", "directUpgrade", "minAppVersionCode",
        "minAppRequireAppVersion", "requiresAppVersion", "SasUpgradeGateSheet",
        "UpgradePromptWidget", "/api/config/new_feature/dialog",
        "A new version of Genspark is available"
    };

    private NativeProbeHook() { }

    public static int install(ClassLoader cl) {
        if (!Config.get().nativeScan) return 0;
        Guard.run("NativeProbe.scan", new Guard.Action() {
            @Override public void run() { scan(); }
        });
        return 1; // مسح واحد يكفي — يُعلَن منجزًا دائمًا
    }

    private static void scan() {
        UpdateEventLogger.log("native-scan", "env: api=" + EnvInfo.apiLevel()
                + " abi=" + (EnvInfo.abis().length > 0 ? EnvInfo.abis()[0] : "?")
                + " nativeLibDir=" + EnvInfo.nativeLibraryDir());

        // خريطة المكتبات الملفوظة فعليًا
        Set<String> mapped = new LinkedHashSet<String>();
        BufferedReader r = null;
        try {
            r = new BufferedReader(new FileReader("/proc/self/maps"));
            String line;
            while ((line = r.readLine()) != null) {
                int i = line.lastIndexOf(' ');
                if (i < 0) continue;
                String path = line.substring(i + 1).trim();
                if (path.endsWith(".so") && (path.indexOf("libapp.so") >= 0 || path.indexOf("libflutter.so") >= 0)) {
                    mapped.add(path);
                }
                if (mapped.size() > 32) break;
            }
        } catch (Throwable t) {
            Guardian_record("NativeProbe.maps", t);
        } finally {
            if (r != null) try { r.close(); } catch (Throwable ignored) { }
        }

        if (mapped.isEmpty()) {
            // احتياط: مسار nativeLibraryDir المعلن
            String dir = EnvInfo.nativeLibraryDir();
            for (String n : LIB_NAMES) {
                if (dir != null && dir.length() > 0) {
                    File f = new File(dir, n);
                    if (f.exists()) mapped.add(f.getAbsolutePath());
                }
            }
        }

        if (mapped.isEmpty()) {
            UpdateEventLogger.log("native-scan", "no libapp.so/libflutter.so mapped — التطبيق قد يستخدم مسارًا مختلفًا");
            UxLog.w("NativeProbe: no native libs mapped");
            return;
        }

        for (String p : mapped) {
            File f = new File(p);
            if (!f.exists() || !f.canRead()) {
                UpdateEventLogger.log("native-scan", "cannot read " + p);
                continue;
            }
            long size = f.length();
            String sha = sha256(f);
            String hits = countMarkers(f);
            String msg = "lib=" + f.getName()
                    + " size=" + size
                    + " sha256=" + sha
                    + " markers={" + hits + "}"
                    + " path=" + p;
            UpdateEventLogger.log("native-scan", msg);
            UxLog.i("NativeProbe: " + msg);
        }
    }

    /** عدّ ظهور كل نصّ قرار داخل الملف (قراءة تدفّقية — لا تحميل للملف كاملًا). */
    private static String countMarkers(File f) {
        int[] counts = new int[MARKERS.length];
        InputStream in = null;
        try {
            in = new FileInputStream(f);
            byte[] buf = new byte[262144];
            int carry = 0;
            int n;
            StringBuilder window = new StringBuilder(300);
            while ((n = in.read(buf)) > 0) {
                String chunk = new String(buf, 0, n, "ISO-8859-1");
                window.append(chunk);
                String s = window.toString();
                for (int i = 0; i < MARKERS.length; i++) {
                    int idx = 0;
                    while ((idx = s.indexOf(MARKERS[i], idx)) >= 0) { counts[i]++; idx += MARKERS[i].length(); }
                }
                int keep = Math.max(carry, 128);
                if (s.length() > keep) window.delete(0, s.length() - keep);
                carry = keep;
            }
        } catch (Throwable t) {
            Guardian_record("NativeProbe.countMarkers", t);
        } finally {
            if (in != null) try { in.close(); } catch (Throwable ignored) { }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MARKERS.length; i++) {
            if (sb.length() > 0) sb.append(',');
            sb.append(MARKERS[i]).append('=').append(counts[i]);
        }
        return sb.toString();
    }

    private static String sha256(File f) {
        InputStream in = null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            in = new FileInputStream(f);
            byte[] buf = new byte[262144];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            byte[] d = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", Integer.valueOf(b & 0xFF)));
            return sb.toString();
        } catch (Throwable t) {
            Guardian_record("NativeProbe.sha256", t);
            return "unavailable";
        } finally {
            if (in != null) try { in.close(); } catch (Throwable ignored) { }
        }
    }

    private static void Guardian_record(String where, Throwable t) {
        Guard.record(where, t);
    }
}
