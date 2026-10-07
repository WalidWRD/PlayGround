package de.robv.android.xposed;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Compile-time stub of XposedHelpers (runtime provided by the patch framework). */
public final class XposedHelpers {

    public static Class<?> findClass(String className, ClassLoader classLoader) { return null; }
    public static Class<?> findClassIfExists(String className, ClassLoader classLoader) { return null; }

    public static Method findMethodExact(Class<?> clazz, String methodName, Class<?>... parameterTypes) { return null; }
    public static Method findMethodExactIfExists(Class<?> clazz, String methodName, Class<?>... parameterTypes) { return null; }

    public static Constructor<?> findConstructorExact(Class<?> clazz, Class<?>... parameterTypes) { return null; }

    public static Field findField(Class<?> clazz, String fieldName) { return null; }
    public static Field findFieldIfExists(Class<?> clazz, String fieldName) { return null; }

    public static XC_MethodHook.Unhook findAndHookMethod(Class<?> clazz, String methodName, Object... parameterTypesAndCallback) { return null; }
    public static XC_MethodHook.Unhook findAndHookMethod(String className, ClassLoader classLoader, String methodName, Object... parameterTypesAndCallback) { return null; }

    public static Object getObjectField(Object obj, String fieldName) { return null; }
    public static void setObjectField(Object obj, String fieldName, Object value) {}
    public static boolean getBooleanField(Object obj, String fieldName) { return false; }
    public static void setBooleanField(Object obj, String fieldName, boolean value) {}
    public static int getIntField(Object obj, String fieldName) { return 0; }
    public static void setIntField(Object obj, String fieldName, int value) {}
    public static long getLongField(Object obj, String fieldName) { return 0L; }
    public static Object getStaticObjectField(Class<?> clazz, String fieldName) { return null; }
    public static void setStaticObjectField(Class<?> clazz, String fieldName, Object value) {}

    public static Object callMethod(Object obj, String methodName, Object... args) { return null; }
    public static Object callStaticMethod(Class<?> clazz, String methodName, Object... args) { return null; }
    public static boolean callStaticBooleanMethod(Class<?> clazz, String methodName, Object... args) { return false; }
    public static Object newInstance(Class<?> clazz, Object... args) { return null; }

    private XposedHelpers() {}
}
