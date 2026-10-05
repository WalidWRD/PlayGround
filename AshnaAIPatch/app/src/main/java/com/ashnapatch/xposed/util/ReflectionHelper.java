package com.ashnapatch.xposed.util;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * مساعدات انعكاس آمنة — تستخدم lpparam.classLoader دائماً بدل الأسماء الثابتة،
 * وتتعامل مع الحزم المضغوطة/المحمية (multidex / PairIP) عبر الفحص التدريجي
 * والتجاوز الصامت عند غياب الكلاس (توافق مع الإصدارات الأعلى والأقل).
 */
public final class ReflectionHelper {
    private ReflectionHelper() {}

    /**
     * يبحث عن أول كلاس موجود من قائمة مرشحين عبر classLoader الهدف.
     * يعيد null عند الغياب (حالة طبيعية — لا تُسجَّل كخطأ).
     */
    public static Class<?> findFirstExisting(ClassLoader loader, String... candidates) {
        if (loader == null || candidates == null) return null;
        for (String name : candidates) {
            if (name == null || name.isEmpty()) continue;
            try {
                Class<?> c = XposedHelpers.findClassIfExists(name, loader);
                if (c != null) return c;
            } catch (Throwable t) {
                LogUtil.warn("find skipped: " + name, t);
            }
        }
        return null;
    }

    /** هوك آمن لكل طرق تحمل اسماً معيناً في كلاس يُكتشف بالانعكاس. */
    public static int hookAllMethodsSafe(Class<?> clazz, String methodName, XC_MethodHook hook) {
        if (clazz == null || methodName == null || hook == null) return 0;
        try {
            return XposedBridge.hookAllMethods(clazz, methodName, hook).size();
        } catch (Throwable t) {
            LogUtil.warn("hookAll failed: " + clazz.getName() + "#" + methodName, t);
            return 0;
        }
    }

    /** قراءة حقل نصي بأمان. */
    public static String getStringFieldSafe(Object obj, String field) {
        try {
            Object v = XposedHelpers.getObjectField(obj, field);
            return v == null ? null : String.valueOf(v);
        } catch (Throwable t) {
            return null;
        }
    }

    /** استدعاء ميثود بلا وسائط بأمان مع قيمة افتراضية عند الفشل. */
    @SuppressWarnings("unchecked")
    public static <T> T callNoArgSafe(Object obj, String method, T fallback) {
        try {
            Method m = obj.getClass().getMethod(method);
            m.setAccessible(true);
            Object r = m.invoke(obj);
            return r == null ? fallback : (T) r;
        } catch (Throwable t) {
            return fallback;
        }
    }
}
