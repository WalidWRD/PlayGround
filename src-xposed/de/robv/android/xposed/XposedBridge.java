package de.robv.android.xposed;

import java.lang.reflect.Constructor;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/** Compile-time stub of XposedBridge (runtime provided by the patch framework). */
public final class XposedBridge {

    public static void log(String text) {}
    public static void log(Throwable t) {}

    public static XC_MethodHook.Unhook hookMethod(Member hookMethod, XC_MethodHook callback) {
        return null;
    }

    public static Set<XC_MethodHook.Unhook> hookAllMethods(Class<?> hookClass, String methodName, XC_MethodHook callback) {
        return new HashSet<XC_MethodHook.Unhook>();
    }

    public static Set<XC_MethodHook.Unhook> hookAllConstructors(Class<?> hookClass, XC_MethodHook callback) {
        return new HashSet<XC_MethodHook.Unhook>();
    }

    private XposedBridge() {}
}
