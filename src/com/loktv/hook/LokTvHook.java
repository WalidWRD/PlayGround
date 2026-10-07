package com.loktv.hook;

import android.app.Application;
import android.content.Context;
import android.content.pm.ApplicationInfo;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Entry point (declared in assets/xposed_init).
 *
 * Strategy for packed / protected APKs:
 *   1) try the classloader handed to us by the loader,
 *   2) additionally hook Application.attachBaseContext()/onCreate() so we grab the
 *      REAL application classloader (which for packed apps is created after our
 *      first callback), and re-run the engine on it if it differs.
 * Everything is wrapped: the module can never break the host app.
 */
public final class LokTvHook implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    private static final String LOG_TAG = "LOKTV-HOOK-PRO";

    @Override
    public void initZygote(StartupParam startupParam) {
        try {
            XposedBridge.log(LOG_TAG + " | zygote init | modulePath=" + startupParam.modulePath);
        } catch (Throwable ignored) {}
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            if (lpparam == null) return;
            String pkg = safePackage(lpparam);
            if (!isTarget(pkg)) return;

            XposedBridge.log(LOG_TAG + " | target detected: " + pkg);

            final ClassLoader baseLoader = safeClassLoader(lpparam);
            Engine.apply(baseLoader, pkg, null);

            installApplicationHook(pkg);
        } catch (Throwable t) {
            safeLog("handleLoadPackage failed (contained)", t);
        }
    }

    /**
     * Captures the definitive classloader + Context of the running application.
     * On packed APKs the loader created in handleLoadPackage is often the stub
     * loader, so we must re-apply on the real one.
     */
    private void installApplicationHook(final String pkg) {
        try {
            XposedHelpers.findAndHookMethod(Application.class, "attachBaseContext", Context.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                Context ctx = (Context) param.args[0];
                                if (ctx == null) return;
                                ClassLoader real = ctx.getClassLoader();
                                if (real != null) {
                                    XposedBridge.log(LOG_TAG + " | real classloader acquired: "
                                            + real.getClass().getName());
                                    Engine.apply(real, pkg, ctx);
                                    scheduleRetry(ctx, pkg, 1);
                                }
                            } catch (Throwable t) {
                                safeLog("attachBaseContext hook failed", t);
                            }
                        }
                    });
        } catch (Throwable t) {
            safeLog("installApplicationHook failed (contained)", t);
        }

        try {
            XposedHelpers.findAndHookMethod(Application.class, "onCreate",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            try {
                                Object app = param.thisObject;
                                if (app instanceof Application) {
                                    Engine.apply(((Application) app).getClassLoader(), pkg, (Application) app);
                                }
                            } catch (Throwable t) {
                                safeLog("onCreate hook failed", t);
                            }
                        }
                    });
        } catch (Throwable t) {
            safeLog("onCreate install failed (contained)", t);
        }
    }

    // ------------------------------------------------------------------ utils

    private static String safePackage(XC_LoadPackage.LoadPackageParam lp) {
        try {
            if (lp.packageName != null) return lp.packageName;
        } catch (Throwable ignored) {}
        try {
            ApplicationInfo ai = lp.appInfo;
            if (ai != null && ai.packageName != null) return ai.packageName;
        } catch (Throwable ignored) {}
        try {
            return lp.processName;
        } catch (Throwable ignored) {}
        return null;
    }

    private static ClassLoader safeClassLoader(XC_LoadPackage.LoadPackageParam lp) {
        try {
            return lp.classLoader;
        } catch (Throwable t) {
            return LokTvHook.class.getClassLoader();
        }
    }

    /** Accepts the exact package and every configured alias (multi-version safe). */
    public static boolean isTarget(String pkg) {
        if (pkg == null) return false;
        String lower = pkg.toLowerCase();
        for (String alias : ModuleInfo.TARGET_ALIASES) {
            if (alias == null || alias.length() == 0) continue;
            String a = alias.toLowerCase();
            if (lower.equals(a) || lower.contains(a)) return true;
        }
        return false;
    }

    /**
     * v2.1.0: packed apps sometimes expose dex files a moment AFTER
     * attachBaseContext. One delayed re-apply catches late dex without
     * risking an apply loop (Engine caps applies per loader).
     */
    private static void scheduleRetry(final Context ctx, final String pkg, final int attempt) {
        if (attempt > 2) return;
        try {
            android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
            final int next = attempt + 1;
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        ClassLoader cl = ctx != null ? ctx.getClassLoader()
                                : LokTvHook.class.getClassLoader();
                        if (cl != null) Engine.apply(cl, pkg, ctx);
                    } catch (Throwable ignored) {}
                }
            }, attempt == 1 ? 1500L : 4000L);
        } catch (Throwable ignored) {}
    }

    private static void safeLog(String msg, Throwable t) {
        try {
            XposedBridge.log(LOG_TAG + " | " + msg + " :: " + Log.describe(t));
        } catch (Throwable ignored) {}
    }
}
