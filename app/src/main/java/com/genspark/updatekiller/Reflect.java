package com.genspark.updatekiller;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/* loaded from: classes.dex */
public final class Reflect {
    private Reflect() {
    }

    public static Class<?> findClass(String str, ClassLoader classLoader) {
        if (str == null) {
            return null;
        }
        if (classLoader != null) {
            try {
                return Class.forName(str, false, classLoader);
            } catch (Throwable unused) {
            }
        }
        try {
            try {
                return Class.forName(str);
            } catch (Throwable unused2) {
                return Class.forName(str, false, ClassLoader.getSystemClassLoader());
            }
        } catch (Throwable unused3) {
            return null;
        }
    }

    public static boolean hasClass(String str, ClassLoader classLoader) {
        return findClass(str, classLoader) != null;
    }

    public static Object invoke(Object obj, Class<?> cls, String str, Object[] objArr) {
        if (str == null) {
            return null;
        }
        if (cls == null) {
            try {
                cls = obj.getClass();
            } catch (Throwable th) {
                Guardian.record("Reflect.invoke(" + str + ")", th);
            }
        }
        for (Method method : cls.getMethods()) {
            if (str.equals(method.getName()) && method.getParameterTypes().length == objArr.length) {
                try {
                    if (!method.isAccessible()) {
                        method.setAccessible(true);
                    }
                    return method.invoke(obj, objArr);
                } catch (Throwable unused) {
                    return null;
                }
            }
        }
        return null;
    }

    public static List<Method> methodsNamed(Class<?> cls, String str) {
        ArrayList arrayList = new ArrayList();
        if (cls != null && str != null) {
            try {
                for (Method method : cls.getDeclaredMethods()) {
                    if (method.getName().equals(str)) {
                        arrayList.add(method);
                    }
                }
            } catch (Throwable th) {
                Guardian.record("Reflect.methodsNamed", th);
            }
        }
        return arrayList;
    }

    public static List<Method> allMethods(Class<?> cls) {
        ArrayList arrayList = new ArrayList();
        if (cls == null) {
            return arrayList;
        }
        try {
            for (Method method : cls.getDeclaredMethods()) {
                arrayList.add(method);
            }
        } catch (Throwable th) {
            Guardian.record("Reflect.allMethods", th);
        }
        return arrayList;
    }

    public static List<Method> methodsBySignature(Class<?> cls, String[] strArr, String[] strArr2) {
        ArrayList arrayList = new ArrayList();
        if (cls == null) {
            return arrayList;
        }
        try {
            for (Method method : cls.getDeclaredMethods()) {
                if ((strArr == null || nameMatches(method.getName(), strArr)) && (strArr2 == null || paramsMatch(method.getParameterTypes(), strArr2))) {
                    arrayList.add(method);
                }
            }
        } catch (Throwable th) {
            Guardian.record("Reflect.methodsBySignature", th);
        }
        return arrayList;
    }

    private static boolean nameMatches(String str, String[] strArr) {
        for (String str2 : strArr) {
            if (str2 != null && str.contains(str2)) {
                return true;
            }
        }
        return false;
    }

    private static boolean paramsMatch(Class<?>[] clsArr, String[] strArr) {
        int length = strArr.length;
        int i = 0;
        while (true) {
            boolean z = true;
            if (i >= length) {
                return true;
            }
            String str = strArr[i];
            int length2 = clsArr.length;
            int i2 = 0;
            while (true) {
                if (i2 < length2) {
                    Class<?> cls = clsArr[i2];
                    if (cls != null && cls.getName().contains(str)) {
                        break;
                    }
                    i2++;
                } else {
                    z = false;
                    break;
                }
            }
            if (!z) {
                return false;
            }
            i++;
        }
    }

    public static boolean hook(Method method, XC_MethodHook xC_MethodHook) {
        if (method != null && xC_MethodHook != null) {
            try {
                if (Modifier.isAbstract(method.getModifiers())) {
                    return false;
                }
                XposedBridge.hookMethod(method, xC_MethodHook);
                return true;
            } catch (Throwable th) {
                Guardian.record("Reflect.hook(" + method.getName() + ")", th);
            }
        }
        return false;
    }

    public static int hookAllNamed(Class<?> cls, String str, XC_MethodHook xC_MethodHook) {
        Iterator<Method> it = methodsNamed(cls, str).iterator();
        int i = 0;
        while (it.hasNext()) {
            if (hook(it.next(), xC_MethodHook)) {
                i++;
            }
        }
        return i;
    }

    public static int hookBySignature(Class<?> cls, String[] strArr, String[] strArr2, XC_MethodHook xC_MethodHook) {
        Iterator<Method> it = methodsBySignature(cls, strArr, strArr2).iterator();
        int i = 0;
        while (it.hasNext()) {
            if (hook(it.next(), xC_MethodHook)) {
                i++;
            }
        }
        return i;
    }

    public static Object getField(Object obj, String str) {
        Field field;
        if (obj != null && str != null) {
            try {
                Class<?> cls = obj.getClass();
                try {
                    field = cls.getDeclaredField(str);
                } catch (NoSuchFieldException unused) {
                    field = cls.getField(str);
                }
                field.setAccessible(true);
                return field.get(obj);
            } catch (Throwable unused2) {
            }
        }
        return null;
    }

    public static void setField(Object obj, String str, Object obj2) {
        Field field;
        if (obj == null || str == null) {
            return;
        }
        try {
            Class<?> cls = obj.getClass();
            try {
                field = cls.getDeclaredField(str);
            } catch (NoSuchFieldException unused) {
                field = cls.getField(str);
            }
            field.setAccessible(true);
            field.set(obj, obj2);
        } catch (Throwable unused2) {
        }
    }
}
