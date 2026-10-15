package com.apphook.log;

import android.content.pm.ApplicationInfo;

import dalvik.system.DexFile;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * مسح فئات APK بحثاً عن كلمات الاشتراك — يكشف الفئات الحقيقية بعد R8.
 * يعمل على sourceDir (+ splitSourceDirs) من ApplicationInfo — لا يحتاج Context.
 */
public final class DexScanner {
    private DexScanner() {}

    private static final String TAG = "SA.Scan";

    private static final String[] KEYWORDS = {
            "premium", "vip", "subscri", "billing", "purchase",
            "entitle", "membership", "plan", "paywall", "upgrade"
    };

    public static void scan(ApplicationInfo ai) {
        List<String> hits = scanTargets(ai);
        SafeHook.log(TAG + ": found " + hits.size() + " candidate classes");
        int n = 0;
        for (String c : hits) {
            if (n++ >= 200) break;
            SafeHook.log(TAG + "  -> " + c);
        }
        SafeHook.recordCall("scan", "dex_candidates", 0, hits.size(), hits.size());
    }

    public static List<String> scanTargets(ApplicationInfo ai) {
        List<String> all = new ArrayList<>();
        if (ai == null) {
            SafeHook.logSafe(TAG, "ApplicationInfo null");
            return all;
        }
        List<String> paths = new ArrayList<>();
        if (ai.sourceDir != null) paths.add(ai.sourceDir);
        try {
            if (ai.splitSourceDirs != null) {
                for (String s : ai.splitSourceDirs) {
                    if (s != null) paths.add(s);
                }
            }
        } catch (Throwable ignored) {}
        for (String path : paths) {
            DexFile dex = null;
            try {
                dex = new DexFile(path);
                Enumeration<String> it = dex.entries();
                while (it.hasMoreElements()) {
                    String name;
                    try {
                        name = it.nextElement();
                    } catch (Throwable ignored) {
                        continue;
                    }
                    if (name == null) continue;
                    String l = name.toLowerCase();
                    for (String kw : KEYWORDS) {
                        if (l.contains(kw)) {
                            all.add(name);
                            break;
                        }
                    }
                    if (all.size() >= 500) break;
                }
            } catch (Throwable t) {
                SafeHook.logError(TAG + ".scan(" + path + ")", t);
            } finally {
                if (dex != null) {
                    try {
                        dex.close();
                    } catch (Throwable ignored) {}
                }
            }
        }
        return all;
    }
}
