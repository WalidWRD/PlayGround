package de.robv.android.xposed;

import de.robv.android.xposed.XC_LoadPackage;

/* loaded from: classes.dex */
public interface IXposedHookLoadPackage extends IXposedMod {
    void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable;
}
