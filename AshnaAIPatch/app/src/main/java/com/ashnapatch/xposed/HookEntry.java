package com.ashnapatch.xposed;

import com.ashnapatch.xposed.hooks.AdBlocker;
import com.ashnapatch.xposed.hooks.DialogSuppressor;
import com.ashnapatch.xposed.hooks.NetworkDiag;
import com.ashnapatch.xposed.hooks.UpdateBlocker;
import com.ashnapatch.xposed.hooks.WebViewPatcher;
import com.ashnapatch.xposed.util.LogUtil;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * نقطة الدخول — تُسجَّل في assets/xposed_init.
 *
 * متوافق مع: Xposed / LSPosed / LSPatch / NPatch / HKP بدون روت.
 * يستخدم lpparam.classLoader والبحث بالانعكاس فقط — لا أسماء كلاسات
 * ثابتة ملزمة، ويتحمّل غياب الكلاسات (توافق أعلى/أقل + حزم محمية PairIP).
 *
 * مبدأ السلامة: كل مجموعة هوكات معزولة في try/catch مستقل —
 * فشل مجموعة لا يكسر التطبيق ولا يمنع باقي المجموعات.
 */
public class HookEntry implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            if (lpparam == null || lpparam.packageName == null) return;
            if (!ModuleInfo.TARGET_PACKAGE.equals(lpparam.packageName)) return;

            // الوصف المدمج — يظهر في سجل التشغيل (مدير الموديولات/LSPosed log).
            PrefsManager.warmup();
            LogUtil.info("==============================================");
            LogUtil.info(ModuleInfo.DESCRIPTION);
            LogUtil.info(ModuleInfo.CHANGELOG);
            LogUtil.info(ModuleInfo.SCOPE_NOTE);
            try {
                LogUtil.info(PrefsManager.stateLine());
            } catch (Throwable ignored) {
            }
            LogUtil.info("Process: " + lpparam.processName + " | classLoader ready.");
            LogUtil.info("==============================================");

            try {
                UpdateBlocker.apply(lpparam);
            } catch (Throwable t) {
                LogUtil.warn("UpdateBlocker crashed (isolated)", t);
            }
            try {
                DialogSuppressor.apply();
            } catch (Throwable t) {
                LogUtil.warn("DialogSuppressor crashed (isolated)", t);
            }
            try {
                AdBlocker.apply(lpparam);
            } catch (Throwable t) {
                LogUtil.warn("AdBlocker crashed (isolated)", t);
            }
            try {
                WebViewPatcher.apply();
            } catch (Throwable t) {
                LogUtil.warn("WebViewPatcher crashed (isolated)", t);
            }
            try {
                NetworkDiag.apply();
            } catch (Throwable t) {
                LogUtil.warn("NetworkDiag crashed (isolated)", t);
            }

            LogUtil.info("All hooks installed safely (crash-guarded).");
        } catch (Throwable t) {
            // آخر خط دفاع: لا نرمي أبداً — التطبيق يجب أن يعمل حتى لو فشل الموديول.
            try {
                LogUtil.warn("HookEntry fatal (swallowed to protect app)", t);
            } catch (Throwable ignored) {
            }
        }
    }
}
