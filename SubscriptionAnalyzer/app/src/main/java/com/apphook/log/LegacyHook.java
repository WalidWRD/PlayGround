package com.apphook.log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Shim legacy للباتشرات القديمة — نفس طبقات v6.0 مع بوابات Config.
 * الدخول الحديث هو {@link MainHook} عبر META-INF/xposed/java_init.list.
 */
public final class LegacyHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            if (lpparam == null) return;
            String pkg = lpparam.packageName;
            if (!MainHook.inScope(pkg)) return;
            if (lpparam.classLoader == null) return;
            Config.runtimePackage = pkg;
            XposedBridge.log("[SA] " + ModuleInfo.describe());
            LegacyBridge.installAll(lpparam);
            XposedBridge.log("[SA] legacy layers installed for: " + pkg
                    + " [MODE=" + Config.modeName() + "]");
        } catch (Throwable t) {
            try {
                XposedBridge.log("[SA/ERR] LegacyHook ignored");
            } catch (Throwable ignored) {}
        }
    }
}
