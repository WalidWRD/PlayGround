package com.genspark.updatekiller;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * طبقة الانعكاس الآمنة (v2.0.0) — أساس العمل على أي إصدار (أعلى/أقل) وأي حزمة
 * مضغوطة أو محمية أو مشوّشة (R8/ProGuard):
 *  1) لا استيراد لأي نوع من تطبيق الهدف أو Flutter وقت الترجمة.
 *  2) لا أسماء ثابتة: بحث بالاسم، وباللمحات، وبأنواع المُعدّات، وبعدد الوسائط.
 *  3) ربط كل الـoverloads (لا نكتفي بأول دالة).
 *  4) لا استثناء يصل إلى التطبيق (كل خطأ → Guard).
 */
public final class Reflect {

    private Reflect() { }

    /** يُعيد الفئة عبر lpparam.classLoader أو مُحمِّل الموديول (للفئات الإطارية). */
    public static Class<?> findClass(String name, ClassLoader cl) {
        if (name == null) return null;
        try { if (cl != null) return Class.forName(name, false, cl); } catch (Throwable ignored) { }
        try { return Class.forName(name); } catch (Throwable ignored) { }
        try { return Class.forName(name, false, ClassLoader.getSystemClassLoader()); } catch (Throwable ignored) { }
        return null;
    }

    public static boolean hasClass(String name, ClassLoader cl) { return findClass(name, cl) != null; }

    public static boolean hasClass(String name) { return hasClass(name, null); }

    /** استدعاء آمن بالانعكاس (بالاسم + عدد الوسائط). للدوال الساكنة مرّر target=null وcls=fئة التصريح. */
    public static Object invoke(Object target, Class<?> cls, String methodName, Object[] args) {
        if (methodName == null) return null;
        Object[] a = (args == null) ? new Object[0] : args;
        try {
            Class<?> c = (cls != null) ? cls : (target == null ? null : target.getClass());
            if (c == null) return null;
            for (Method m : c.getMethods()) {
                if (!methodName.equals(m.getName())) continue;
                if (m.getParameterTypes().length != a.length) continue;
                try {
                    if (!m.isAccessible()) m.setAccessible(true);
                    return m.invoke(target, a);
                } catch (Throwable t) { return null; }
            }
        } catch (Throwable t) { Guardian.record("Reflect.invoke(" + methodName + ")", t); }
        return null;
    }

    public static Object invokeStatic(String clsName, String methodName, Object[] args, ClassLoader cl) {
        Class<?> c = findClass(clsName, cl);
        if (c == null) return null;
        return invoke(null, c, methodName, args);
    }

    /** استدعاء أول دالة بلا وسائط تحمل هذا الاسم (يشمل الوراثة). */
    public static Object invokeNoArg(Object target, String methodName) {
        if (target == null || methodName == null) return null;
        try {
            for (Method m : target.getClass().getMethods()) {
                if (!methodName.equals(m.getName())) continue;
                if (m.getParameterTypes().length != 0) continue;
                try {
                    if (!m.isAccessible()) m.setAccessible(true);
                    return m.invoke(target);
                } catch (Throwable t) { return null; }
            }
        } catch (Throwable t) { Guardian.record("Reflect.invokeNoArg(" + methodName + ")", t); }
        return null;
    }

    public static List<Method> methodsNamed(Class<?> cls, String name) {
        List<Method> out = new ArrayList<Method>();
        if (cls == null || name == null) return out;
        try {
            for (Method m : cls.getDeclaredMethods()) if (name.equals(m.getName())) out.add(m);
        } catch (Throwable t) { Guardian.record("Reflect.methodsNamed", t); }
        return out;
    }

    public static List<Method> allMethods(Class<?> cls) {
        List<Method> out = new ArrayList<Method>();
        if (cls == null) return out;
        try { for (Method m : cls.getDeclaredMethods()) out.add(m); }
        catch (Throwable t) { Guardian.record("Reflect.allMethods", t); }
        return out;
    }

    /** بحث بالمُعدّات و/أو اللمحات في الاسم — يصمد أمام التشويش. */
    public static List<Method> methodsBySignature(Class<?> cls, String[] nameHints, String[] paramHints) {
        List<Method> out = new ArrayList<Method>();
        if (cls == null) return out;
        try {
            for (Method m : cls.getDeclaredMethods()) {
                if (nameHints != null && nameHints.length > 0 && !nameMatches(m.getName(), nameHints)) continue;
                if (paramHints != null && paramHints.length > 0 && !paramsMatch(m.getParameterTypes(), paramHints)) continue;
                out.add(m);
            }
        } catch (Throwable t) { Guardian.record("Reflect.methodsBySignature", t); }
        return out;
    }

    /** بحث بأنواع المُعدّات بالاسم الكامل (يعمل حتى لو كان الاسم مشوّشًا). */
    public static List<Method> methodsByParamNames(Class<?> cls, String[] paramTypeNames) {
        List<Method> out = new ArrayList<Method>();
        if (cls == null || paramTypeNames == null) return out;
        try {
            for (Method m : cls.getDeclaredMethods()) {
                Class<?>[] ps = m.getParameterTypes();
                if (ps.length != paramTypeNames.length) continue;
                boolean ok = true;
                for (int i = 0; i < ps.length; i++) {
                    if (ps[i] == null || !ps[i].getName().equals(paramTypeNames[i])) { ok = false; break; }
                }
                if (ok) out.add(m);
            }
        } catch (Throwable t) { Guardian.record("Reflect.methodsByParamNames", t); }
        return out;
    }

    public static List<Constructor<?>> constructors(Class<?> cls) {
        List<Constructor<?>> out = new ArrayList<Constructor<?>>();
        if (cls == null) return out;
        try { for (Constructor<?> c : cls.getDeclaredConstructors()) out.add(c); }
        catch (Throwable t) { Guardian.record("Reflect.constructors", t); }
        return out;
    }

    private static boolean nameMatches(String name, String[] hints) {
        if (name == null) return false;
        String l = name.toLowerCase(Locale.US);
        for (String h : hints) {
            if (h == null) continue;
            if (l.indexOf(h.toLowerCase(Locale.US)) >= 0) return true;
        }
        return false;
    }

    private static boolean paramsMatch(Class<?>[] params, String[] hints) {
        for (String h : hints) {
            if (h == null) continue;
            boolean found = false;
            for (Class<?> p : params) {
                if (p != null && p.getName().indexOf(h) >= 0) { found = true; break; }
            }
            if (!found) return false;
        }
        return true;
    }

    public static boolean hook(Method m, XC_MethodHook cb) {
        if (m == null || cb == null) return false;
        try {
            if (Modifier.isAbstract(m.getModifiers())) return false;
            if (Modifier.isNative(m.getModifiers())) return false;
            XposedBridge.hookMethod(m, cb);
            return true;
        } catch (Throwable t) {
            Guardian.record("Reflect.hook(" + m.getName() + ")", t);
            return false;
        }
    }

    public static boolean hookCtor(Constructor<?> c, XC_MethodHook cb) {
        if (c == null || cb == null) return false;
        try {
            XposedBridge.hookMethod((Member) c, cb);
            return true;
        } catch (Throwable t) {
            Guardian.record("Reflect.hookCtor", t);
            return false;
        }
    }

    public static int hookAllNamed(Class<?> cls, String name, XC_MethodHook cb) {
        int n = 0;
        for (Method m : methodsNamed(cls, name)) if (hook(m, cb)) n++;
        return n;
    }

    public static int hookBySignature(Class<?> cls, String[] nameHints, String[] paramHints, XC_MethodHook cb) {
        int n = 0;
        for (Method m : methodsBySignature(cls, nameHints, paramHints)) if (hook(m, cb)) n++;
        return n;
    }

    public static int hookByParamNames(Class<?> cls, String[] paramTypeNames, XC_MethodHook cb) {
        int n = 0;
        for (Method m : methodsByParamNames(cls, paramTypeNames)) if (hook(m, cb)) n++;
        return n;
    }

    public static int hookConstructors(Class<?> cls, XC_MethodHook cb) {
        int n = 0;
        for (Constructor<?> c : constructors(cls)) if (hookCtor(c, cb)) n++;
        return n;
    }

    /** يربط كل دالة غير ساكنة تُطابق مُرشِّحًا مخصصًا. */
    public interface MethodFilter { boolean accept(Method m); }

    public static int hookWhere(Class<?> cls, MethodFilter f, XC_MethodHook cb) {
        int n = 0;
        if (cls == null || f == null) return 0;
        for (Method m : allMethods(cls)) {
            try { if (f.accept(m) && hook(m, cb)) n++; } catch (Throwable t) { Guardian.record("Reflect.hookWhere", t); }
        }
        return n;
    }

    public static Object getField(Object target, String fieldName) {
        if (target == null || fieldName == null) return null;
        try {
            Class<?> c = (target instanceof Class) ? (Class<?>) target : target.getClass();
            Field f = null;
            try { f = c.getDeclaredField(fieldName); }
            catch (NoSuchFieldException e) { f = c.getField(fieldName); }
            f.setAccessible(true);
            return f.get((target instanceof Class) ? null : target);
        } catch (Throwable t) { return null; }
    }

    public static Object getFieldDeep(Object target, String fieldName) {
        if (target == null || fieldName == null) return null;
        try {
            Class<?> c = (target instanceof Class) ? (Class<?>) target : target.getClass();
            while (c != null) {
                try {
                    Field f = c.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    return f.get((target instanceof Class) ? null : target);
                } catch (NoSuchFieldException e) { c = c.getSuperclass(); }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    public static Object getStaticField(Class<?> cls, String fieldName) {
        return getField(cls, fieldName);
    }

    public static boolean setField(Object target, String fieldName, Object value) {
        if (target == null || fieldName == null) return false;
        try {
            Class<?> c = target.getClass();
            Field f;
            try { f = c.getDeclaredField(fieldName); }
            catch (NoSuchFieldException e) { f = c.getField(fieldName); }
            f.setAccessible(true);
            f.set(target, value);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isVoid(Method m) {
        try { return m != null && m.getReturnType() == void.class; } catch (Throwable t) { return true; }
    }

    public static String paramTypes(Method m) {
        if (m == null) return "";
        try {
            StringBuilder sb = new StringBuilder();
            for (Class<?> c : m.getParameterTypes()) {
                if (sb.length() > 0) sb.append(',');
                sb.append(c == null ? "?" : c.getName());
            }
            return sb.toString();
        } catch (Throwable t) { return "?"; }
    }

    public static String returnType(Method m) {
        try { return (m == null) ? "?" : m.getReturnType().getName(); } catch (Throwable t) { return "?"; }
    }

    /** هل تُنفّذ الفئة هذه الواجهة (بالمقارنة بالاسم، مع تصفّح السلسلة والوراثة)؟ */
    public static boolean implementsInterface(Class<?> c, String ifaceName, ClassLoader cl) {
        if (c == null || ifaceName == null) return false;
        try {
            Class<?> cur = c;
            while (cur != null) {
                if (scanInterfaces(cur.getInterfaces(), ifaceName, 0)) return true;
                cur = cur.getSuperclass();
            }
        } catch (Throwable t) { Guardian.record("Reflect.implementsInterface", t); }
        return false;
    }

    private static boolean scanInterfaces(Class<?>[] ifaces, String ifaceName, int depth) {
        if (ifaces == null || depth > 6) return false;
        for (Class<?> i : ifaces) {
            if (i == null) continue;
            if (ifaceName.equals(i.getName())) return true;
            try { if (scanInterfaces(i.getInterfaces(), ifaceName, depth + 1)) return true; } catch (Throwable ignored) { }
        }
        return false;
    }

    /** كائن Application الحقيقي للتطبيق (انعكاسي — لا استيراد لأنواع مخفية). */
    public static Object currentApplication() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Method m = at.getDeclaredMethod("currentApplication");
            m.setAccessible(true);
            return m.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }
}
