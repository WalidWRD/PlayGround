package com.kakuaudit.observe.core;

import java.lang.reflect.Method;

/**
 * Higher/lower version handling. Every version-dependent call goes through
 * here with reflective lookup + safe fallback. No direct references to
 * APIs that don't exist on minSdk (24) outside guarded blocks.
 */
public final class VersionCompat {
    private VersionCompat() {}

    public static int apiLevel() {
        try {
            Class<?> v = Class.forName("android.os.Build$VERSION");
            return v.getField("SDK_INT").getInt(null);
        } catch (Throwable t) {
            return 24;
        }
    }

    public static boolean atLeast(int api) {
        return apiLevel() >= api;
    }

    /** Reflective method lookup that returns null on older versions. */
    public static Method method(Class<?> clazz, String name, Class<?>... params) {
        if (clazz == null) return null;
        try {
            return clazz.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            try {
                return clazz.getDeclaredMethod(name, params);
            } catch (Throwable ignore) {
                return null;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    /** Invoke if present, else return fallback. Never throws. */
    public static Object invokeOr(Object target, Method m, Object fallback, Object... args) {
        if (m == null) return fallback;
        try {
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (Throwable t) {
            return fallback;
        }
    }
}
