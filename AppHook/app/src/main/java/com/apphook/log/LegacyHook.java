package com.apphook.log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Shim legacy للتوافق مع الباتشرات القديمة (LSPatch قديم / بعض NPatch).
 * يشارك نفس طبقات الهوك عبر الانعكاس الآمن — لا يرمي أبداً.
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
            XposedBridge.log("[AppHook] " + ModuleInfo.describe());
            LegacyBridge.installAll(lpparam);
            XposedBridge.log("[AppHook] legacy layers installed for: " + pkg);
        } catch (Throwable t) {
            try {
                XposedBridge.log("[AppHook/ERR] LegacyHook ignored: " + t);
            } catch (Throwable ignored) {}
        }
    }
}
