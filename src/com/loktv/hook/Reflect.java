package com.loktv.hook;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/** Null-safe reflection toolbox. Nothing here ever throws to the caller. */
public final class Reflect {

    public static Class<?> findClass(ClassLoader cl, String name) {
        if (cl == null || name == null) return null;
        try {
            return Class.forName(name, false, cl);
        } catch (Throwable t) {
            return null;
        }
    }

    public static Class<?> firstClass(ClassLoader cl, String... names) {
        for (String n : names) {
            Class<?> c = findClass(cl, n);
            if (c != null) return c;
        }
        return null;
    }

    public static Method method(Class<?> c, String name, Class<?>... params) {
        if (c == null) return null;
        Class<?> k = c;
        while (k != null) {
            try {
                Method m = k.getDeclaredMethod(name, params);
                m.setAccessible(true);
                return m;
            } catch (Throwable t) {
                k = k.getSuperclass();
            }
        }
        return null;
    }

    /** Any declared method with this exact name and zero parameters. */
    public static Method methodNoArg(Class<?> c, String name) {
        if (c == null) return null;
        try {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterTypes().length == 0) {
                    m.setAccessible(true);
                    return m;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static Field field(Class<?> c, String name) {
        if (c == null) return null;
        Class<?> k = c;
        while (k != null) {
            try {
                Field f = k.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (Throwable t) {
                k = k.getSuperclass();
            }
        }
        return null;
    }

    public static Object get(Field f, Object target) {
        try { return f == null ? null : f.get(target); } catch (Throwable t) { return null; }
    }

    public static boolean set(Field f, Object target, Object value) {
        try {
            if (f == null) return false;
            f.set(target, value);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static Object call(Method m, Object target, Object... args) {
        try { return m == null ? null : m.invoke(target, args); } catch (Throwable t) { return null; }
    }

    // ---- signatures -------------------------------------------------------

    public static boolean isStatic(Method m) {
        try { return Modifier.isStatic(m.getModifiers()); } catch (Throwable t) { return false; }
    }

    public static boolean isAbstract(Method m) {
        try { return Modifier.isAbstract(m.getModifiers()); } catch (Throwable t) { return true; }
    }

    public static boolean isNoArg(Method m) {
        try { return m.getParameterTypes().length == 0; } catch (Throwable t) { return false; }
    }

    public static boolean returnsBoolean(Method m) {
        try {
            Class<?> r = m.getReturnType();
            return r == boolean.class || r == Boolean.class;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean returnsVoid(Method m) {
        try { return m.getReturnType() == void.class; } catch (Throwable t) { return false; }
    }

    public static boolean returnsString(Method m) {
        try { return m.getReturnType() == String.class; } catch (Throwable t) { return false; }
    }

    public static boolean returnsInt(Method m) {
        try {
            Class<?> r = m.getReturnType();
            return r == int.class || r == Integer.class;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean returnsLong(Method m) {
        try {
            Class<?> r = m.getReturnType();
            return r == long.class || r == Long.class;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean nameMatches(String name, String[] keys) {
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.US());
        for (String k : keys) if (lower.contains(k)) return true;
        return false;
    }

    /** Tiny helper so we do not depend on java.util.Locale at call sites. */
    private static final class Locale {
        static java.util.Locale US() { return java.util.Locale.US; }
    }

    private Reflect() {}
}
