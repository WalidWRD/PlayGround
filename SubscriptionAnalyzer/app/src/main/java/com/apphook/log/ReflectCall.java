package com.apphook.log;

import java.lang.reflect.Method;

/**
 * استدعاء انعكاسي آمن (بديل XposedHelpers.callMethod غير الموجود في API 102).
 * كل استدعاء داخل try/catch — الفشل يُرجع null ولا يكسر التطبيق.
 */
public final class ReflectCall {
    private ReflectCall() {}

    public static Object call(Object recv, String name, Object... args) {
        if (recv == null || name == null) return null;
        try {
            Class<?> c = recv.getClass();
            for (Method m : c.getMethods()) {
                if (!m.getName().equals(name)) continue;
                if (m.getParameterTypes().length != (args == null ? 0 : args.length)) continue;
                try {
                    m.setAccessible(true);
                    return m.invoke(recv, args);
                } catch (Throwable ignored) {}
            }
            // fallback: declared (private)
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(name)) continue;
                if (m.getParameterTypes().length != (args == null ? 0 : args.length)) continue;
                try {
                    m.setAccessible(true);
                    return m.invoke(recv, args);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static Object getField(Object recv, String field) {
        if (recv == null) return null;
        try {
            java.lang.reflect.Field f = recv.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return f.get(recv);
        } catch (Throwable ignored) {
            return null;
        }
    }
}
