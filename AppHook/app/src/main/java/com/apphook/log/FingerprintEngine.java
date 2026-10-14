package com.apphook.log;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * محرك البحث بالانعكاس والبصمة — لا يعتمد على أسماء ثابتة.
 *
 * <p>يستخدم {@code lpparam.classLoader} (في API الجديد:
 * {@code PackageReadyParam.getClassLoader()}) حصراً، ويبحث بـ:</p>
 * <ul>
 *   <li>بصمة التوقيع: اسم جزئي + نوع الإرجاع + عدد الوسائط.</li>
 *   <li>بصمة السلوك: boolean/int مرشحة كأعلام premium.</li>
 *   <li>سلسلة استراتيجيات: ثابت مستقر أولاً، ثم ديناميكي، ثم تجاهل آمن.</li>
 * </ul>
 */
public final class FingerprintEngine {
    private FingerprintEngine() {}

    /** نتيجة الكاش — تُحفظ لتجنب التحليل كل مرة (انظر docs/ANALYSIS.md) */
    public static final class Hit {
        public final Class<?> clazz;
        public final Method method;
        public final String strategy;
        Hit(Class<?> c, Method m, String s) { clazz = c; method = m; strategy = s; }
    }

    /** حاول تحميل كلاس — null بدل ClassNotFound (لا كراش). */
    public static Class<?> findClassQuiet(ClassLoader cl, String name) {
        if (cl == null || name == null) return null;
        try {
            return Class.forName(name, false, cl);
        } catch (Throwable t) {
            return null;
        }
    }

    /** ابحث عن Method بتوقيع مرن: اسم دقيق أو جزئي + نوع إرجاع + عدد وسائط. */
    public static Method findMethodByFingerprint(Class<?> clazz,
                                                String exactOrPartial,
                                                Class<?> returnType,
                                                int argCount) {
        if (clazz == null) return null;
        try {
            for (Method m : clazz.getDeclaredMethods()) {
                try {
                    boolean nameOk = m.getName().equals(exactOrPartial)
                            || m.getName().toLowerCase().contains(exactOrPartial.toLowerCase());
                    if (!nameOk) continue;
                    if (returnType != null && !returnType.isAssignableFrom(m.getReturnType())) {
                        // تساهل int/Integer و boolean/Boolean
                        if (!isBoxingCompatible(returnType, m.getReturnType())) continue;
                    }
                    if (argCount >= 0 && m.getParameterTypes().length != argCount) continue;
                    if (Modifier.isAbstract(m.getModifiers())) continue;
                    m.setAccessible(true);
                    return m;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static boolean isBoxingCompatible(Class<?> a, Class<?> b) {
        if (a == null || b == null) return false;
        if (a.isAssignableFrom(b) || b.isAssignableFrom(a)) return true;
        String x = a.getSimpleName().toLowerCase();
        String y = b.getSimpleName().toLowerCase();
        return (x.contains("int") && y.contains("int"))
                || (x.contains("bool") && y.contains("bool"));
    }

    /**
     * مسح ديناميكي لأعلام premium: أي Method بلا وسائط يرجع boolean/int
     * واسمه يحتوي كلمة من القاموس — يُرجع قائمة Hits.
     */
    public static List<Hit> scanPremiumFlags(Class<?> clazz) {
        List<Hit> out = new ArrayList<>();
        if (clazz == null) return out;
        String[] dict = {"premium", "vip", "pro", "paid", "subscrib", "member",
                "gold", "plus", "elite", "unlock", "entitle"};
        try {
            for (Method m : clazz.getDeclaredMethods()) {
                try {
                    if (m.getParameterTypes().length != 0) continue;
                    Class<?> r = m.getReturnType();
                    if (r != boolean.class && r != Boolean.class
                            && r != int.class && r != Integer.class) continue;
                    String n = m.getName().toLowerCase();
                    for (String k : dict) {
                        if (n.contains(k)) {
                            m.setAccessible(true);
                            out.add(new Hit(clazz, m, "dynamic-scan:" + k));
                            break;
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return out;
    }
}
