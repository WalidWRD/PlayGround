package com.ashnapatch.xposed.hooks;

import android.app.Dialog;

import com.ashnapatch.xposed.Config;
import com.ashnapatch.xposed.PrefsManager;
import com.ashnapatch.xposed.util.LogUtil;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * تعطيل التحديث الإجباري وحوار التحديث الفوري.
 *
 * الاستراتيجية (متوافقة مع الأعلى/الأقل + الحزم المحمية):
 * 1) Play In-App Updates بالانعكاس: إن وُجدت تُعطَّل startUpdateFlow (no-op)،
 *    وإن غابت (حالة AshnaAI 1.0.6) يُتجاهل بصمت — لا كراش.
 * 2) expo-updates بالانعكاس: تعطيل checkForUpdate وما شابه إن وُجدت.
 * 3) حوارات التحديث القادمة من طبقة الويب: تُخفى عبر WebViewPatcher (JS)
 *    + تُصفَّى هنا على مستوى Dialog بعد العرض ثم تُغلق فوراً إن طابقت
 *    كلمات التحديث ولم تطابق القائمة البيضاء.
 */
public final class UpdateBlocker {
    private UpdateBlocker() {}

    public static void apply(final XC_LoadPackage.LoadPackageParam lpparam) {
        if (!PrefsManager.getUpdateBlock() && !Config.ENABLE_UPDATE_BLOCK) {
            LogUtil.info("UpdateBlocker: disabled by user toggle — skipped.");
            return;
        }
        try {
            blockPlayInAppUpdates(lpparam);
        } catch (Throwable t) {
            LogUtil.warn("UpdateBlocker.play failed", t);
        }
        try {
            blockExpoUpdates(lpparam);
        } catch (Throwable t) {
            LogUtil.warn("UpdateBlocker.expo failed", t);
        }
        try {
            filterUpdateDialogs();
        } catch (Throwable t) {
            LogUtil.warn("UpdateBlocker.dialogs failed", t);
        }
    }

    private static void blockPlayInAppUpdates(final XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> mgr = null;
        try {
            mgr = XposedHelpers.findClassIfExists(
                    "com.google.android.play.core.appupdate.AppUpdateManagerImpl",
                    lpparam.classLoader);
        } catch (Throwable t) {
            LogUtil.warn("play class lookup failed", t);
        }
        if (mgr == null) {
            LogUtil.info("Play In-App Updates: not present (expected on AshnaAI 1.0.6) — skipped safely.");
            return;
        }
        try {
            final Class<?> infoCls = XposedHelpers.findClassIfExists(
                    "com.google.android.play.core.appupdate.AppUpdateInfo",
                    lpparam.classLoader);
            final Class<?> optsCls = XposedHelpers.findClassIfExists(
                    "com.google.android.play.core.appupdate.AppUpdateOptions",
                    lpparam.classLoader);
            // خطاف كل الحملات (Activity / Launcher / بدون خيارات) — مقاوم لاختلاف الإصدارات.
            int n = XposedBridge.hookAllMethods(mgr, "startUpdateFlow", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (!PrefsManager.getUpdateBlock()) return;
                                LogUtil.info("Blocked Play startUpdateFlow (forced update disabled).");
                                // الحملات الحديثة ترجع Task<Boolean>؛ القديمة boolean — غطِّ الحالتين.
                                try {
                                    param.setResult(Boolean.FALSE);
                                } catch (Throwable ignored) {
                                    param.setResult(null);
                                }
                            } catch (Throwable t) {
                                LogUtil.warn("startUpdateFlow hook body failed", t);
                            }
                        }
                    }).size();
            int m = XposedBridge.hookAllMethods(mgr, "requestUpdateFlow", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (!PrefsManager.getUpdateBlock()) return;
                                LogUtil.info("Blocked Play requestUpdateFlow.");
                                param.setResult(null);
                            } catch (Throwable t) {
                                LogUtil.warn("requestUpdateFlow hook body failed", t);
                            }
                        }
                    }).size();
            int k = XposedBridge.hookAllMethods(mgr, "completeUpdate", new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (!PrefsManager.getUpdateBlock()) return;
                                LogUtil.info("Blocked Play completeUpdate (silent no-op).");
                                param.setResult(null);
                            } catch (Throwable t) {
                                LogUtil.warn("completeUpdate hook body failed", t);
                            }
                        }
                    }).size();
            LogUtil.info("Play In-App Updates disabled (start:" + n + " req:" + m + " complete:" + k
                    + " infoCls=" + (infoCls != null) + " optsCls=" + (optsCls != null) + ").");
        } catch (Throwable t) {
            LogUtil.warn("Play hook not applied (version mismatch?) — continuing safely", t);
        }
    }

    private static void blockExpoUpdates(final XC_LoadPackage.LoadPackageParam lpparam) {
        Class<?> ctrl = null;
        try {
            ctrl = XposedHelpers.findClassIfExists(
                    "expo.modules.updates.UpdatesController",
                    lpparam.classLoader);
        } catch (Throwable t) {
            LogUtil.warn("expo class lookup failed", t);
        }
        if (ctrl == null) {
            LogUtil.info("expo-updates controller: not found — skipped safely.");
            return;
        }
        // أسماء محتملة عبر الإصدارات — يُجرَّب كل اسم ويُتجاهل الغائب.
        String[] methods = new String[]{
                "checkForUpdateAsync", "fetchUpdateAsync", "reloadAsync", "checkForUpdate"
        };
        for (final String m : methods) {
            try {
                XposedHelpers.findAndHookMethod(ctrl, m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        try {
                            LogUtil.info("Blocked expo-updates." + m + " (OTA disabled).");
                            param.setResult(null);
                        } catch (Throwable t) {
                            LogUtil.warn("expo hook body failed", t);
                        }
                    }
                });
            } catch (Throwable ignored) {
                // غياب الميثود في هذا الإصدار أمر طبيعي — تابع.
            }
        }
        LogUtil.info("expo-updates: hooks attempted (reflection, version-tolerant).");
    }

    /** فلترة حوارات التحديث — مفوَّضة للمرشّح الموحّد (هوك واحد، بلا ازدواج). */
    private static void filterUpdateDialogs() {
        DialogFilter.ensureHooked();
    }
}
