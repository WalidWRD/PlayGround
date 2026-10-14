package com.pro.hook.universal.core;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * سياق الهوك — يُبنى مرة واحدة من lpparam ويُمرر لكل الاستراتيجيات.
 * القاعدة الذهبية: استخدم ctx.classLoader دائماً، ولا تستخدم classLoader الموديول.
 */
public final class HookContext {
    public final XC_LoadPackage.LoadPackageParam lpparam;
    public final ClassLoader classLoader;
    public final String packageName;
    public final String processName;
    public final boolean isFirstApplication;

    public HookContext(XC_LoadPackage.LoadPackageParam lpparam) {
        this.lpparam = lpparam;
        this.classLoader = lpparam.classLoader; // <-- الأساس
        this.packageName = lpparam.packageName;
        this.processName = lpparam.processName;
        this.isFirstApplication = lpparam.isFirstApplication;
    }
}
