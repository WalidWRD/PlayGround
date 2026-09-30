package com.genspark.updatekiller.hooks;

import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Discovery;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.UpdateEventLogger;
import com.genspark.updatekiller.UxLog;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;

/**
 * جديد 2.0.0 — «مُبطِل بوابات التحديث العام» (Universal Gate Neutralizer).
 *
 * الفكرة: حتى لو غيّر الخادم مفاتيح JSON، أو أعاد المطوّر تسمية الأصناف، أو استخدم
 * إصدار أحدث بوابة تحديث جديدة تمامًا — يبقى في البنية **صنف بوابة/حوار تحديث**.
 * فنكتشفه بنيويًا من فهرس DEX (اسم يحتوي upgrade / appversion / update+dialog|gate|prompt|sheet)
 * ونُبطل فعله بلا أي اعتماد على نص أو مفتاح:
 *
 *  1) أي دالة عرض (show / present / open) في هذه الأصناف → بعد التنفيذ نستدعي
 *     dismiss()/hide()/cancel() انعكاسيًا على نفس الكائن، فيُغلق الحوار/البوابة فورًا.
 *  2) أي دالة استفهام منطقي (is / should / need / force / has / can) بلا وسائط تُعيد boolean
 *     داخل هذه الأصناف → نُعيد false، فيصبح شرط الفرض غير محقّق.
 *  3) كل ذلك مغلّف بـGuard، ومحدود العدد، وقابل للإيقاف بمفتاح universalGate.
 */
public final class UniversalGateHook {

    private static final Set<String> HOOKED = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static volatile int total = 0;
    private static final int MAX_CLASSES = 80;
    private static final int MAX_HOOKS_PER_CLASS = 24;

    private UniversalGateHook() { }

    public static int install(ClassLoader cl) {
        if (!Config.get().universalGate || cl == null) return 0;
        int before = total;
        try {
            List<Class<?>> targets = Discovery.classesMatching(cl, new Discovery.ClassFilter() {
                @Override public boolean accept(String name) {
                    if (name == null) return false;
                    String l = name.toLowerCase(Locale.US);
                    if (l.indexOf("upgrade") >= 0) return true;
                    if (l.indexOf("appversion") >= 0) return true;
                    if (l.indexOf("forceupdate") >= 0) return true;
                    if (l.indexOf("update") >= 0) {
                        return l.indexOf("dialog") >= 0 || l.indexOf("gate") >= 0
                            || l.indexOf("prompt") >= 0 || l.indexOf("sheet") >= 0
                            || l.indexOf("checker") >= 0 || l.indexOf("banner") >= 0;
                    }
                    if (l.indexOf("versioncheck") >= 0 || l.indexOf("minversion") >= 0) return true;
                    return false;
                }
            }, MAX_CLASSES);

            for (Class<?> k : targets) {
                if (k == null || k.isInterface()) continue;
                if (!HOOKED.add("gate:" + k.getName())) continue;
                int n = hookClass(k);
                total += n;
                if (n > 0) UxLog.i("UniversalGate: " + k.getName() + " → " + n + " hook(s)");
            }
        } catch (Throwable t) {
            Guard.record("UniversalGate.install", t);
        }

        int added = total - before;
        if (added > 0) UxLog.i("UniversalGate: +" + added + " hook(s) (total " + total + ")");
        return total;
    }

    private static int hookClass(final Class<?> k) {
        int n = 0;
        for (Method m : Reflect.allMethods(k)) {
            if (n >= MAX_HOOKS_PER_CLASS) break;
            try {
                final String mn = m.getName().toLowerCase(Locale.US);
                final int pc = m.getParameterTypes().length;
                final Class<?> rt = m.getReturnType();

                // (1) دوال العرض → إغلاق فوري
                boolean showing = (mn.startsWith("show") || mn.startsWith("present") || mn.startsWith("open"))
                        && pc <= 2 && rt == void.class;
                if (showing && Reflect.hook(m, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        Guard.run("UniversalGate.show", () -> {
                            if (p.thisObject == null) return;
                            boolean closed = invokeAny(p.thisObject, new String[]{"dismiss", "hide", "cancel"});
                            UpdateEventLogger.log("gate", "update gate shown → " + k.getName() + "." + mn
                                    + " (auto-close=" + closed + ") — دليل ظهور بوابة تحديث");
                            UxLog.i("UniversalGate: gate '" + k.getName() + "' shown → " + (closed ? "closed" : "no-dismiss"));
                        });
                    }
                })) n++;

                // (2) استفهام منطقي بلا وسائط → false
                boolean predicate = pc == 0 && rt == boolean.class
                        && (mn.startsWith("is") || mn.startsWith("should") || mn.startsWith("need")
                            || mn.startsWith("force") || mn.startsWith("has") || mn.startsWith("can")
                            || mn.startsWith("must"));
                if (predicate && Reflect.hook(m, new XC_MethodHook() {
                    @Override protected void afterHookedMethod(MethodHookParam p) {
                        Guard.run("UniversalGate.predicate", () -> {
                            if (Boolean.TRUE.equals(p.getResult())) {
                                p.setResult(Boolean.FALSE);
                                UxLog.d("UniversalGate: " + k.getName() + "." + mn + " → false");
                            }
                        });
                    }
                })) n++;
            } catch (Throwable t) {
                Guard.record("UniversalGate.hookClass", t);
            }
        }
        return n;
    }

    private static boolean invokeAny(Object target, String[] names) {
        for (String nm : names) {
            Object r = Reflect.invokeNoArg(target, nm);
            if (r != null) return true;
        }
        return false;
    }
}
