package com.pro.hook.universal.core;

import com.pro.hook.universal.util.LogX;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

import java.lang.reflect.Member;

/**
 * تغليف آمن لكل هوك — يمنع أي كراش يكسر التطبيق.
 * <ul>
 *   <li>كل before/after داخل try/catch.</li>
 *   <li>فشل الهوك = لوج فقط + متابعة، لا رمي استثناء.</li>
 *   <li>hookQuiet: يرجع Unhook أو null بدون كراش.</li>
 * </ul>
 */
public final class SafeHook {
    private SafeHook() {}

    public interface Before { void run(XC_MethodHook.MethodHookParam p) throws Throwable; }
    public interface After  { void run(XC_MethodHook.MethodHookParam p) throws Throwable; }

    public static XC_MethodHook.Unhook hookQuiet(Member target, Before before, After after) {
        if (target == null) return null;
        try {
            XC_MethodHook hook = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (before == null) return;
                    try {
                        before.run(param);
                    } catch (Throwable t) {
                        LogX.w("SafeHook.before failed: " + target + " -> " + t);
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (after == null) return;
                    try {
                        after.run(param);
                    } catch (Throwable t) {
                        LogX.w("SafeHook.after failed: " + target + " -> " + t);
                    }
                }
            };
            return XposedBridge.hookMethod(target, hook);
        } catch (Throwable t) {
            LogX.w("hookMethod failed (skipped safely): " + target + " -> " + t);
            return null;
        }
    }

    public static XC_MethodHook.Unhook hookBeforeQuiet(Member target, Before before) {
        return hookQuiet(target, before, null);
    }

    public static XC_MethodHook.Unhook hookAfterQuiet(Member target, After after) {
        return hookQuiet(target, null, after);
    }

    /** فك الهوك بأمان */
    public static void unhookQuiet(XC_MethodHook.Unhook u) {
        if (u == null) return;
        try {
            u.unhook();
        } catch (Throwable t) {
            LogX.w("unhook failed: " + t);
        }
    }
}
