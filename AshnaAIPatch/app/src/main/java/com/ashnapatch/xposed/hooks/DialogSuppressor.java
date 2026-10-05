package com.ashnapatch.xposed.hooks;

import android.widget.Toast;

import com.ashnapatch.xposed.Config;
import com.ashnapatch.xposed.PrefsManager;
import com.ashnapatch.xposed.util.LogUtil;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * كتم الرسائل التحذيرية المزعجة (من التطبيق أو من المشغّل).
 *
 * - Dialog: مفوَّض للمرشّح الموحّد DialogFilter (هوك واحد بلا ازدواج).
 * - Toast: التقاط النص عبر makeText (يعمل على أندرويد 12+ حيث getView=null)
 *   مع مسار احتياطي بالانعكاس للإصدارات القديمة.
 * كل الفحوص داخل try/catch — أي فشل يعني ترك السلوك الأصلي (لا كراش).
 */
public final class DialogSuppressor {
    private DialogSuppressor() {}

    /** آخر نص مرّر لـ Toast.makeText في هذا الخيط (لأندرويد 12+). */
    private static final ThreadLocal<String> lastToastText = new ThreadLocal<>();

    public static void apply() {
        // الحوارات عبر المرشّح الموحّد.
        try {
            DialogFilter.ensureHooked();
        } catch (Throwable t) {
            LogUtil.warn("DialogSuppressor.dialog failed", t);
        }
        try {
            hookMakeTextCapture();
        } catch (Throwable t) {
            LogUtil.warn("DialogSuppressor.maketext failed", t);
        }
        try {
            hookToastShow();
        } catch (Throwable t) {
            LogUtil.warn("DialogSuppressor.toast failed", t);
        }
    }

    /** يلتقط نص makeText قبل show — الحل الموثوق لأندرويد 12+. */
    private static void hookMakeTextCapture() {
        try {
            XposedBridge.hookAllMethods(Toast.class, "makeText", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        if (param.args != null && param.args.length >= 2
                                && param.args[1] instanceof CharSequence) {
                            lastToastText.set(String.valueOf(param.args[1]));
                        }
                    } catch (Throwable ignored) {
                    }
                }
            });
            LogUtil.info("Toast.makeText capture: active.");
        } catch (Throwable t) {
            LogUtil.warn("Toast.makeText hook failed", t);
        }
    }

    private static void hookToastShow() {
        try {
            XposedHelpers.findAndHookMethod(Toast.class, "show", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (!PrefsManager.getWarningFilter()) return;
                        if (!(param.thisObject instanceof Toast)) return;
                        Toast toast = (Toast) param.thisObject;
                        String text = lastToastText.get();
                        try {
                            lastToastText.remove();
                        } catch (Throwable ignored) {
                        }
                        if (text == null) text = legacyToastText(toast);
                        if (text == null) return;
                        if (Config.containsAny(text, Config.WHITELIST_KEYWORDS)) return;
                        if (Config.containsAny(text, Config.WARNING_KEYWORDS)) {
                            LogUtil.info("Suppressed noisy toast.");
                            try {
                                toast.cancel();
                            } catch (Throwable ignored) {
                            }
                            param.setResult(null);
                        }
                    } catch (Throwable t) {
                        LogUtil.warn("toast filter body failed", t);
                    }
                }
            });
            LogUtil.info("Noisy toast filter: active.");
        } catch (Throwable t) {
            LogUtil.warn("Toast.show hook failed", t);
        }
    }

    /** مسار الإصدارات القديمة: mTN.mNextView ثم getView(). */
    private static String legacyToastText(Toast toast) {
        try {
            try {
                Object tn = XposedHelpers.getObjectField(toast, "mTN");
                if (tn != null) {
                    try {
                        Object mNextView = XposedHelpers.getObjectField(tn, "mNextView");
                        if (mNextView instanceof android.view.View) {
                            String s = viewText((android.view.View) mNextView);
                            if (s != null) return s;
                        }
                    } catch (Throwable ignored) {
                    }
                }
            } catch (Throwable ignored) {
            }
            try {
                android.view.View v = toast.getView();
                return viewText(v);
            } catch (Throwable ignored) {
                return null;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    private static String viewText(android.view.View v) {
        try {
            if (v == null) return null;
            if (v instanceof android.widget.TextView) {
                CharSequence cs = ((android.widget.TextView) v).getText();
                return cs == null ? null : cs.toString();
            }
            if (v instanceof android.view.ViewGroup) {
                StringBuilder sb = new StringBuilder();
                android.view.ViewGroup g = (android.view.ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) {
                    String s = viewText(g.getChildAt(i));
                    if (s != null) sb.append(s).append('\n');
                }
                return sb.toString();
            }
            return null;
        } catch (Throwable t) {
            return null;
        }
    }
}
