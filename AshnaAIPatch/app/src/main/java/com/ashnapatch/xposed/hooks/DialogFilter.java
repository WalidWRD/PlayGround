package com.ashnapatch.xposed.hooks;

import android.app.Dialog;

import com.ashnapatch.xposed.Config;
import com.ashnapatch.xposed.PrefsManager;
import com.ashnapatch.xposed.util.LogUtil;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

/**
 * مرشّح الحوارات الموحّد — هوك واحد فقط لـ Dialog.show يقوم بالتصنيف:
 * تحديث إجباري / تحذير مزعج / مُتجاهَل (قائمة بيضاء أو لا تطابق).
 *
 * يمنع ازدواج الهوك السابق (UpdateBlocker + DialogSuppressor كانا
 * يخطَفان show معاً) ويقرأ المفاتيح الحيّة من PrefsManager.
 */
public final class DialogFilter {
    private DialogFilter() {}

    public enum Decision { IGNORED, DISMISSED_UPDATE, DISMISSED_WARNING }

    private static volatile boolean hooked;
    /** منع فيض السجل: نفس النص خلال 1.5 ثانية يُغلق بصمت دون إعادة التسجيل. */
    private static volatile long lastLogMs;
    private static volatile int lastLogHash;
    private static volatile int dismissedUpdate;
    private static volatile int dismissedWarning;

    public static void ensureHooked() {
        if (hooked) return;
        synchronized (DialogFilter.class) {
            if (hooked) return;
            try {
                XposedHelpers.findAndHookMethod(Dialog.class, "show",
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) {
                                try {
                                    if (!(param.thisObject instanceof Dialog)) return;
                                    Dialog d = (Dialog) param.thisObject;
                                    String text = DialogText.extract(d);
                                    Decision dec = classify(text);
                                    if (dec == Decision.IGNORED) return;
                                    // إغلاق دائماً، لكن السجل م debounced لمنع الفيض.
                                    try {
                                        d.dismiss();
                                    } catch (Throwable t) {
                                        LogUtil.warn("dialog dismiss failed", t);
                                    }
                                    try {
                                        int h = text.hashCode() ^ dec.ordinal();
                                        long now = System.currentTimeMillis();
                                        if (h == lastLogHash && (now - lastLogMs) < 1500) return;
                                        lastLogHash = h;
                                        lastLogMs = now;
                                        if (dec == Decision.DISMISSED_UPDATE) {
                                            dismissedUpdate++;
                                            LogUtil.info("Dismissed forced-update dialog (#"
                                                    + dismissedUpdate + ").");
                                        } else {
                                            dismissedWarning++;
                                            LogUtil.info("Suppressed noisy warning dialog (#"
                                                    + dismissedWarning + ").");
                                        }
                                    } catch (Throwable ignored) {
                                    }
                                } catch (Throwable t) {
                                    LogUtil.warn("dialog filter body failed", t);
                                }
                            }
                        });
                hooked = true;
                LogUtil.info("DialogFilter: single Dialog.show hook active.");
            } catch (Throwable t) {
                LogUtil.warn("DialogFilter hook failed", t);
            }
        }
    }

    static Decision classify(String text) {
        try {
            if (text == null || text.isEmpty()) return Decision.IGNORED;
            // القائمة البيضاء أولاً دائماً (حماية الدخول/OTP/الدفع).
            if (Config.containsAny(text, Config.WHITELIST_KEYWORDS)) return Decision.IGNORED;
            if (PrefsManager.getUpdateBlock()
                    && Config.containsAny(text, Config.UPDATE_KEYWORDS)) {
                return Decision.DISMISSED_UPDATE;
            }
            if (PrefsManager.getWarningFilter()
                    && Config.containsAny(text, Config.WARNING_KEYWORDS)) {
                return Decision.DISMISSED_WARNING;
            }
            // القوائم المخصصة للمستخدم (مرتبة أدنى من المدمجة، وأدنى من البيضاء).
            try {
                if (PrefsManager.getWarningFilter() && PrefsManager.matchesCustomWarning(text)) {
                    return Decision.DISMISSED_WARNING;
                }
            } catch (Throwable ignored) {
            }
            return Decision.IGNORED;
        } catch (Throwable t) {
            return Decision.IGNORED;
        }
    }
}
