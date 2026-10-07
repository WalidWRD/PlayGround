package de.robv.android.xposed.callbacks;

import android.content.pm.ApplicationInfo;

import de.robv.android.xposed.IXposedHookLoadPackage;

/** Compile-time stub matching the runtime shape of XC_LoadPackage.LoadPackageParam (api-82). */
public final class XC_LoadPackage {

    public static class LoadPackageParam {
        public String packageName;
        public String processName;
        public boolean isFirstApplication;
        public ClassLoader classLoader;
        public ApplicationInfo appInfo;
    }

    private XC_LoadPackage() {}
}
