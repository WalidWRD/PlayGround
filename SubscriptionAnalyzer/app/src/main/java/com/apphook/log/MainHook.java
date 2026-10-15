package com.apphook.log;

import android.util.Log;

import com.apphook.log.hooks.BillingHook;
import com.apphook.log.hooks.FlutterChannelHook;
import com.apphook.log.hooks.IntegrityHook;
import com.apphook.log.hooks.JsonHook;
import com.apphook.log.hooks.NetworkHook;
import com.apphook.log.hooks.PrefsHook;
import com.apphook.log.hooks.RevenueCatHook;
import com.apphook.log.hooks.UpdateGateHook;
import com.apphook.log.hooks.WebViewHook;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * نقطة الدخول الحديثة (libxposed 101/102) — SubscriptionAnalyzer v6.0.
 * الوضع من {@link Config}: ANALYZE (تسجيل فقط) أو PATCH (تعديل + قياس).
 * الهوك الحقيقي في onPackageReady (بعد فك stub التطبيقات المحمية).
 */
public final class MainHook extends XposedModule {

    public MainHook() {
        super();
    }

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        SafeHook.attach(this);
        ModuleInfo.logStartup(this);
        try {
            log(Log.INFO, "SA", "v6.0.0 loaded [MODE=" + Config.modeName() + "] in: "
                    + param.getProcessName());
        } catch (Throwable ignored) {}
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        String pkg;
        try {
            pkg = param.getPackageName();
        } catch (Throwable t) {
            return;
        }
        if (!inScope(pkg)) return;
        SafeHook.log("package loaded (early): " + pkg + " — waiting onPackageReady");
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        String pkg;
        ClassLoader cl;
        try {
            pkg = param.getPackageName();
            cl = param.getClassLoader();
        } catch (Throwable t) {
            SafeHook.logError("onPackageReady/params", t);
            return;
        }
        if (!inScope(pkg)) return;
        if (cl == null) {
            SafeHook.logSafe(pkg, "ClassLoader null — abort safely");
            return;
        }
        Config.runtimePackage = pkg;
        SafeHook.log("package ready: " + pkg + " [MODE=" + Config.modeName() + "]");
        installAll(this, cl, pkg, param.getApplicationInfo());
    }

    static void installAll(XposedInterface fw, ClassLoader cl, String pkg,
                           android.content.pm.ApplicationInfo ai) {
        try {
            DexScanner.scan(ai);
        } catch (Throwable t) {
            SafeHook.logError("DexScanner", t);
        }
        try {
            BillingHook.install(fw, cl);
        } catch (Throwable t) {
            SafeHook.logError("Billing", t);
        }
        try {
            RevenueCatHook.install(fw, cl);
        } catch (Throwable t) {
            SafeHook.logError("RevenueCat", t);
        }
        try {
            FlutterChannelHook.install(fw, cl);
        } catch (Throwable t) {
            SafeHook.logError("Flutter", t);
        }
        try {
            WebViewHook.install(fw, cl);
        } catch (Throwable t) {
            SafeHook.logError("WebView", t);
        }
        try {
            PrefsHook.install(fw, cl);
        } catch (Throwable t) {
            SafeHook.logError("Prefs", t);
        }
        try {
            JsonHook.install(fw, cl);
        } catch (Throwable t) {
            SafeHook.logError("Json", t);
        }
        try {
            NetworkHook.install(fw, cl);
        } catch (Throwable t) {
            SafeHook.logError("Network", t);
        }
        try {
            IntegrityHook.install(fw, cl);
        } catch (Throwable t) {
            SafeHook.logError("Integrity", t);
        }
        try {
            UpdateGateHook.install(fw, cl);
        } catch (Throwable t) {
            SafeHook.logError("UpdateGate", t);
        }
        SafeHook.log("All v6.0 layers installed for: " + pkg
                + " [MODE=" + Config.modeName() + "]");
    }

    static boolean inScope(String pkg) {
        if (pkg == null) return false;
        if (pkg.equals("android")) return false;
        if (pkg.equals("com.android.systemui")) return false;
        if (pkg.equals("com.google.android.gms")) return false;
        if (pkg.equals("com.android.vending")) return false;
        if (pkg.equals(ModuleInfo.PACKAGE)) return false;
        return pkg.startsWith("ai.mainfunc.genspark");
    }
}
