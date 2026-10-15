package com.apphook.log;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * v4.3 — البنية الانعكاسية (methodShapes): ربط بالبصمة لا بالاسم الصريح.
 *
 * <p>الفكرة (كما في HideBuyVip): بدل البحث عن
 * {@code PremiumManager.isPremium} نبحث عن <b>شكل الدالة</b>:
 * {@code ()→boolean} أو {@code (String)→boolean} أو {@code ()→int}
 * داخل فئات مرشحة أصلاً بكلمات الاشتراك — ثم نُجبر
 * {@code false→true} / {@code 0→1}.</p>
 *
 * <p>حدود السلامة (مهم): الربط الخالص بالشكل وحده خطير (قد يقلب
 * {@code isLoading})، لذا الهجين مطبق: الشكل + اسم الدالة يحتوي
 * توكن اشتراك/رصيد/خطة + الفئة المعلنة مرشحة أصلاً. كل ربط PROTECTIVE،
 * dedup، وسقف إجمالي 200 — وكل ضربة تُسجل باسمها لتُدقق في v4.4.</p>
 */
public final class ShapeHook {
    private ShapeHook() {}

    private static final String TAG = "AppHook.Shape";

    private static final Set<String> done =
            Collections.synchronizedSet(new HashSet<String>());

    static final String[] NAME_TOKENS = {
            "premium", "vip", "pro", "paid", "subscri", "member", "entitle",
            "credit", "quota", "plan", "tier", "upgrade", "balance",
            "unlock", "trial", "active", "login"
    };

    private static final String[] SKIP_METHODS = {
            "tostring", "hashcode", "equals", "clone", "finalize",
            "describecontents", "writetoparcel", "onprogresschanged", "poproute"
    };

    private static final int MAX_CLASSES = 120;
    private static final int MAX_HOOKS = 200;

    private static int hooked;

    public static void install(XposedInterface fw, ClassLoader cl, List<String> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            SafeHook.logSafe(TAG, "no candidates");
            return;
        }
        int scanned = 0;
        for (String name : candidates) {
            if (scanned >= MAX_CLASSES || hooked >= MAX_HOOKS) break;
            if (name == null || !done.add(name)) continue;
            Class<?> c = FingerprintEngine.findClassQuiet(cl, name);
            if (c == null) continue;
            scanned++;
            hookShapesIn(fw, c);
        }
        SafeHook.log(TAG + ": shape scan done — classes=" + scanned + " hooked=" + hooked);
    }

    private static void hookShapesIn(XposedInterface fw, Class<?> c) {
        Method[] ms;
        try {
            ms = c.getDeclaredMethods();
        } catch (Throwable ignored) {
            return;
        }
        for (Method m : ms) {
            if (hooked >= MAX_HOOKS) return;
            try {
                if (m.isSynthetic() || m.isBridge()) continue;
                if (Modifier.isAbstract(m.getModifiers())) continue;
                String mn = m.getName();
                if (mn == null) continue;
                String lower = mn.toLowerCase();
                boolean skip = false;
                for (String s : SKIP_METHODS) {
                    if (lower.equals(s)) {
                        skip = true;
                        break;
                    }
                }
                if (skip) continue;
                if (!nameHit(lower)) continue;
                Class<?> rt = m.getReturnType();
                Class<?>[] ps = m.getParameterTypes();
                boolean boolShape = (rt == boolean.class || rt == Boolean.class)
                        && (ps.length == 0 || (ps.length == 1 && ps[0] == String.class));
                boolean intShape = (rt == int.class || rt == Integer.class)
                        && (ps.length == 0 || (ps.length == 1 && ps[0] == String.class));
                if (!boolShape && !intShape) continue;
                m.setAccessible(true);
                final Method target = m;
                final boolean isBool = boolShape;
                fw.hook(target)
                        .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                        .intercept(chain -> {
                            try {
                                Object orig = chain.proceed();
                                if (isBool && Boolean.FALSE.equals(orig)) {
                                    SafeHook.countFeature("shape:" + mn);
                                    return true;
                                }
                                if (!isBool && Integer.valueOf(0).equals(orig)) {
                                    SafeHook.countFeature("shape:" + mn);
                                    return 1;
                                }
                                return orig;
                            } catch (Throwable t) {
                                SafeHook.logError(TAG + "." + mn, t);
                                return chain.proceed();
                            }
                        });
                hooked++;
                SafeHook.log("Hook OK: shape " + mn + " -> " + m);
            } catch (Throwable t) {
                SafeHook.logError(TAG + ".one", t);
            }
        }
    }

    static boolean nameHit(String lower) {
        if (lower == null) return false;
        for (String k : NAME_TOKENS) {
            if (lower.contains(k)) return true;
        }
        return false;
    }
}
