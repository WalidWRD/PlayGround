package de.robv.android.xposed;

import java.lang.reflect.Member;

/** استبوب للترجمة فقط (لا يُضمَّن في الـdex). */
public final class XposedBridge {
    public static void log(String text) { }
    public static void log(Throwable t) { }
    public static XC_MethodHook.Unhook hookMethod(Member hookMethod, XC_MethodHook callback) { return null; }
}
