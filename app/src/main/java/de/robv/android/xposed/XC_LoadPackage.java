package de.robv.android.xposed;

import android.content.pm.ApplicationInfo;
import de.robv.android.xposed.XCallback;

/* loaded from: classes.dex */
public final class XC_LoadPackage extends XCallback {

    public static class LoadPackageParam extends XCallback.Param {
        public ClassLoader classLoader;
        public ApplicationInfo info;
        public boolean isFirstApplication;
        public String packageName;
    }

    public XC_LoadPackage() {
    }

    public XC_LoadPackage(int i) {
        super(i);
    }
}
