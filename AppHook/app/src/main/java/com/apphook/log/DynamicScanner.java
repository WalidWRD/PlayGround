package com.apphook.log;

import android.content.pm.ApplicationInfo;

import dalvik.system.DexFile;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * مسح ديناميكي للفئات المرشحة (Discovery) — يُخبرك بالأسماء الحقيقية
 * بعد Obfuscation/R8 في السجل، لتُبنى v3.1 مركزة على نقطة الفتح الفعلية.
 * يعمل على sourceDir (+ splitSourceDirs) من ApplicationInfo — لا يحتاج Context.
 */
public final class DynamicScanner {
    private DynamicScanner() {}

    private static final String TAG = "AppHook.Scan";

    private static final String[] CLASS_KEYWORDS = {
            "premium", "vip", "subscription", "billing",
            "purchase", "entitlement", "pro"
    };

    public static void scan(ApplicationInfo ai) {
        if (ai == null) {
            SafeHook.logSafe(TAG, "ApplicationInfo null");
            return;
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
        int total = 0;
        for (String path : paths) {
            total += scanOne(path);
        }
        SafeHook.log(TAG + ": scan complete over " + paths.size() + " dex path(s)");
    }

    private static int scanOne(String path) {
        DexFile dex = null;
        try {
            dex = new DexFile(path);
            Enumeration<String> entries = dex.entries();
            List<String> targets = new ArrayList<>();
            while (entries.hasMoreElements()) {
                String name;
                try {
                    name = entries.nextElement();
                } catch (Throwable ignored) {
                    continue;
                }
                if (name == null) continue;
                String lower = name.toLowerCase();
                for (String kw : CLASS_KEYWORDS) {
                    if (lower.contains(kw)) {
                        targets.add(name);
                        break;
                    }
                }
                if (targets.size() >= 200) break; // سقف السجل
            }
            SafeHook.log(TAG + ": " + path + " -> " + targets.size() + " candidate(s)");
            for (String c : targets) {
                SafeHook.log(TAG + "  -> " + c);
            }
            return targets.size();
        } catch (Throwable t) {
            SafeHook.logError(TAG + ".scan(" + path + ")", t);
            return 0;
        } finally {
            if (dex != null) {
                try {
                    dex.close();
                } catch (Throwable ignored) {}
            }
        }
    }
}
