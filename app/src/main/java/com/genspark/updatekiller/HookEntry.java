package com.genspark.updatekiller;

import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.hooks.AdsBlocker;
import com.genspark.updatekiller.hooks.AntiCrash;
import com.genspark.updatekiller.hooks.AutoLoginHook;
import com.genspark.updatekiller.hooks.BlutterTrace;
import com.genspark.updatekiller.hooks.ChannelHook;
import com.genspark.updatekiller.hooks.CreditHook;
import com.genspark.updatekiller.hooks.DialogHook;
import com.genspark.updatekiller.hooks.FlutterDiscoveryHook;
import com.genspark.updatekiller.hooks.GoogleSignInSpoofHook;
import com.genspark.updatekiller.hooks.HttpHook;
import com.genspark.updatekiller.hooks.IntentHook;
import com.genspark.updatekiller.hooks.LoginBannerBlocker;
import com.genspark.updatekiller.hooks.LoginBypassHook;
import com.genspark.updatekiller.hooks.NativeHook;
import com.genspark.updatekiller.hooks.OfficialSiteRedirectBypass;
import com.genspark.updatekiller.hooks.PlayCoreHook;
import com.genspark.updatekiller.hooks.PrefsHook;
import com.genspark.updatekiller.hooks.ServerCheckBypassHook;
import com.genspark.updatekiller.hooks.SubscriptionEnforcer;
import com.genspark.updatekiller.hooks.VersionSpoofHook;
import com.genspark.updatekiller.hooks.WarningDialogBlocker;
import com.genspark.updatekiller.hooks.WebViewHook;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_LoadPackage;

/* loaded from: classes.dex */
public class HookEntry implements IXposedHookLoadPackage {
    private static volatile boolean installed = false;

    @Override // de.robv.android.xposed.IXposedHookLoadPackage
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam loadPackageParam) {
        Guard.run("HookEntry", new Guard.Action() { // from class: com.genspark.updatekiller.HookEntry$$ExternalSyntheticLambda0
            @Override // com.genspark.updatekiller.Guard.Action
            public final void run() throws Throwable {
                HookEntry.lambda$handleLoadPackage$0(loadPackageParam);
            }
        });
    }

    static /* synthetic */ void lambda$handleLoadPackage$0(XC_LoadPackage.LoadPackageParam loadPackageParam) throws Throwable {
        if (loadPackageParam == null) {
            return;
        }
        String str = loadPackageParam.packageName;
        ClassLoader classLoader = loadPackageParam.classLoader;
        if (isTarget(str, classLoader)) {
            if (!Config.get().enabled) {
                UxLog.i("HookEntry: module disabled by config — nothing installed");
                return;
            }
            synchronized (HookEntry.class) {
                if (installed) {
                    return;
                }
                installed = true;
                banner(str);
                int install = VersionSpoofHook.install(classLoader) + 0 + ChannelHook.install(classLoader) + DialogHook.install(classLoader) + IntentHook.install(classLoader) + WebViewHook.install(classLoader) + HttpHook.install(classLoader) + PrefsHook.install(classLoader) + PlayCoreHook.install(classLoader) + NativeHook.install(classLoader) + AdsBlocker.install(classLoader) + BlutterTrace.install(classLoader) + WarningDialogBlocker.install(classLoader) + OfficialSiteRedirectBypass.install(classLoader) + AntiCrash.install(classLoader) + LoginBypassHook.install(classLoader) + AutoLoginHook.install(classLoader) + GoogleSignInSpoofHook.install(classLoader) + LoginBannerBlocker.install(classLoader) + FlutterDiscoveryHook.install(classLoader) + SubscriptionEnforcer.install(classLoader) + ServerCheckBypassHook.install(classLoader) + CreditHook.install(classLoader);
                Guard.setThreshold(Config.get().killSwitchThreshold);
                UxLog.i("HookEntry: ALL DONE — hooks=" + install + " failures=" + Guard.failures() + " killSwitch=" + Guard.isTripped() + " (target " + str + ")");
                UpdateEventLogger.log("session", "ALL DONE — hooks=" + install + " failures=" + Guard.failures() + " killSwitch=" + Guard.isTripped() + " (target " + str + ")");
            }
        }
    }

    private static void banner(String str) {
        try {
            UxLog.i("┌─────────────────────────────────────────────────");
            for (String str2 : BuildInfo.describe().split("\n")) {
                UxLog.i("│ " + str2);
            }
            UxLog.i("│ loaded in: " + str);
            UxLog.i("└─────────────────────────────────────────────────");
        } catch (Throwable unused) {
        }
    }

    private static boolean isTarget(String str, ClassLoader classLoader) {
        if (str != null) {
            for (String str2 : BuildInfo.PACKAGE_NAMES) {
                if (str2.equals(str)) {
                    return true;
                }
            }
            if (str.startsWith(BuildInfo.TARGET_PKG_FREE)) {
                return true;
            }
        }
        return Reflect.hasClass("ai.mainfunc.genspark.MainActivity", classLoader);
    }
}
