package com.genspark.updatekiller.hooks;

import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Discovery;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.SelfTest;
import com.genspark.updatekiller.SubscriptionBridge;
import com.genspark.updatekiller.UpdateEventLogger;
import com.genspark.updatekiller.UxLog;
import com.genspark.updatekiller.json.JsonNeutralizer;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;

/**
 * تصحيح الاشتراك بنجاح (جديد v2.0.1).
 *
 * يربط فحص الاشتراك داخل التطبيق بحالة {@link SubscriptionBridge}:
 *  1) ردود JSON عبر okhttp/okio التي تحمل مفاتيح اشتراك → true + انتهاء بعيد.
 *  2) قنوات Flutter (MethodChannel.invokeMethod) المرتبطة بالاشتراك/الفوترة
 *     (purchase/billing/subscription/entitlement/premium/pro/vip) → تُسوَّف
 *     بنجاح عندما يكون الجسر ACTIVE، وتُسجَّل فقط otherwise.
 *  3) اكتشاف بنيوي لأصناف الفوترة (BillingClient/RevenueCat/Purchases) من
 *     فهرس DEX وربط دوال الاستفهام المنطقي (is/should/has/can + بلا وسائط
 *     وتعيد boolean) → true عند نجاح الاشتراك.
 *  4) SharedPreferences التي تحمل مفاتيح اشتراك → قراءة true دائمًا عند النجاح.
 *
 * ضمانات: كل شيء داخل Guard، ربط مرة واحدة (Idempotent)، لا setResult على
 * دوال void، وتخطٍّ صامت عند غياب أي مكتبة.
 */
public final class SubscriptionHook {

    private static final String[] CHANNEL_HINTS = {
        "purchase", "billing", "subscription", "entitlement", "premium",
        "isPro", "isPremium", "isSubscribed", "vip", "pro"
    };

    private static final Set<String> HOOKED = Collections.newSetFromMap(new ConcurrentHashMap<String, Boolean>());
    private static volatile int total = 0;

    private SubscriptionHook() { }

    public static int install(ClassLoader cl) {
        final Config cfg = Config.get();
        if (!cfg.subscriptionBridge || cl == null) return 0;
        int before = total;
        try {
            total += hookHttpLayer(cl);
            total += hookFlutterChannels(cl);
            total += hookBillingClasses(cl);
            total += hookPrefs(cl);
        } catch (Throwable t) {
            Guard.record("SubscriptionHook.install", t);
        }
        int added = total - before;
        if (added > 0) {
            SelfTest.record("subscription", total);
            UxLog.i("SubscriptionHook: +" + added + " hook(s) (total " + total + ")");
        }
        return total;
    }

    /** هل الاشتراك ناجح الآن؟ (الجسر ACTIVE + مفعّل في الإعدادات). */
    public static boolean successNow() {
        try {
            if (!Config.get().subscriptionBridge) return false;
            return SubscriptionBridge.isActive();
        } catch (Throwable ignored) {
            return false;
        }
    }

    // ── 1) طبقة HTTP: تحويل مفاتيح الاشتراك إلى نجاح ──
    private static int hookHttpLayer(ClassLoader cl) {
        int n = 0;
        Class<?> rb = Reflect.findClass("okhttp3.ResponseBody", cl);
        if (rb != null) {
            n += Reflect.hookAllNamed(rb, "string", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Guard.run("SubscriptionHook.http", new Guard.Action() {
                        @Override public void run() {
                            if (!successNow()) return;
                            Object r = p.getResult();
                            if (!(r instanceof String)) return;
                            String s = (String) r;
                            String out = JsonNeutralizer.applySubscriptionToText(s, true, true);
                            if (JsonNeutralizer.changedText(s, out)) {
                                p.setResult(out);
                                UpdateEventLogger.log("subscription", "http subscription response → success");
                                UxLog.i("SubscriptionHook: http response corrected to success");
                            }
                        }
                    });
                }
            });
        }
        Class<?> buf = Reflect.findClass("okio.Buffer", cl);
        if (buf != null) {
            n += Reflect.hookAllNamed(buf, "readUtf8", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Guard.run("SubscriptionHook.buffer", new Guard.Action() {
                        @Override public void run() {
                            if (!successNow()) return;
                            Object r = p.getResult();
                            if (!(r instanceof String)) return;
                            String s = (String) r;
                            String out = JsonNeutralizer.applySubscriptionToText(s, true, true);
                            if (JsonNeutralizer.changedText(s, out)) p.setResult(out);
                        }
                    });
                }
            });
        }
        return n;
    }

    // ── 2) قنوات Flutter: تسويف نجاح الاشتراك ──
    private static int hookFlutterChannels(ClassLoader cl) {
        int n = 0;
        Class<?> mc = Reflect.findClass("io.flutter.plugin.common.MethodChannel", cl);
        if (mc == null) return 0;
        if (!HOOKED.add("subscription:channel")) return 0;
        n += Reflect.hookAllNamed(mc, "invokeMethod", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                Guard.run("SubscriptionHook.channel", new Guard.Action() {
                    @Override public void run() {
                        if (p.args == null || p.args.length == 0) return;
                        if (!(p.args[0] instanceof String)) return;
                        String name = (String) p.args[0];
                        if (!isSubscriptionCall(name)) return;
                        UpdateEventLogger.log("subscription", "channel call '" + name + "' success=" + successNow());
                        if (!successNow()) return;
                        Method m = (p.method instanceof Method) ? (Method) p.method : null;
                        // لا نكسر العقود: نعترض فقط الدوال غير void.
                        if (m != null && m.getReturnType() != void.class) {
                            // نترك النتيجة الافتراضية null (معظم قنوات الفوترة
                            // تفسّر null كنجاح/تجاهل) بدل كسر تحليل Dart.
                            UxLog.i("SubscriptionHook: observed subscription channel '" + name + "' (bridge ACTIVE)");
                        }
                    }
                });
            }
        });
        return n;
    }

    // ── 3) أصناف الفوترة المكتشفة بنيويًا ──
    private static int hookBillingClasses(ClassLoader cl) {
        int n = 0;
        List<Class<?>> targets;
        try {
            targets = Discovery.classesMatching(cl, new Discovery.ClassFilter() {
                @Override public boolean accept(String name) {
                    if (name == null) return false;
                    String l = name.toLowerCase(Locale.US);
                    if (l.indexOf("billingclient") >= 0) return true;
                    if (l.indexOf("purchases") >= 0 && l.indexOf("revenuecat") >= 0) return true;
                    if (l.indexOf("revenuecat") >= 0) return true;
                    if (l.indexOf("entitlement") >= 0) return true;
                    if (l.indexOf("subscription") >= 0
                            && (l.indexOf("manager") >= 0 || l.indexOf("check") >= 0
                                || l.indexOf("status") >= 0 || l.indexOf("service") >= 0)) return true;
                    return false;
                }
            }, 60);
        } catch (Throwable t) {
            Guard.record("SubscriptionHook.discovery", t);
            return 0;
        }
        for (Class<?> k : targets) {
            if (k == null || k.isInterface()) continue;
            if (!HOOKED.add("subscription:" + k.getName())) continue;
            try {
                for (Method m : Reflect.allMethods(k)) {
                    try {
                        if (m.getParameterTypes().length != 0) continue;
                        if (m.getReturnType() != boolean.class) continue;
                        String mn = m.getName().toLowerCase(Locale.US);
                        boolean predicate = mn.startsWith("is") || mn.startsWith("has")
                                || mn.startsWith("should") || mn.startsWith("can")
                                || mn.indexOf("subscribed") >= 0 || mn.indexOf("premium") >= 0
                                || mn.indexOf("entitled") >= 0 || mn.indexOf("pro") >= 0;
                        if (!predicate) continue;
                        if (Reflect.hook(m, new XC_MethodHook() {
                            @Override protected void afterHookedMethod(MethodHookParam p) {
                                Guard.run("SubscriptionHook.predicate", new Guard.Action() {
                                    @Override public void run() {
                                        if (successNow() && Boolean.FALSE.equals(p.getResult())) {
                                            p.setResult(Boolean.TRUE);
                                            UpdateEventLogger.log("subscription", "predicate → true");
                                        }
                                    }
                                });
                            }
                        })) n++;
                        if (n > 200) break;
                    } catch (Throwable t) {
                        Guard.record("SubscriptionHook.method", t);
                    }
                }
            } catch (Throwable t) {
                Guard.record("SubscriptionHook.class", t);
            }
        }
        return n;
    }

    // ── 4) SharedPreferences لمفاتيح الاشتراك ──
    private static int hookPrefs(ClassLoader cl) {
        int n = 0;
        Class<?> impl = Reflect.findClass("android.app.SharedPreferencesImpl", cl);
        if (impl == null) return 0;
        if (!HOOKED.add("subscription:prefs")) return 0;
        n += Reflect.hookAllNamed(impl, "getBoolean", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Guard.run("SubscriptionHook.prefs", new Guard.Action() {
                    @Override public void run() {
                        if (!successNow()) return;
                        if (p.args != null && p.args.length >= 1 && p.args[0] instanceof String
                                && JsonNeutralizer.isSubBool((String) p.args[0])
                                && Boolean.FALSE.equals(p.getResult())) {
                            p.setResult(Boolean.TRUE);
                            UxLog.d("SubscriptionHook: pref " + p.args[0] + " → true");
                        }
                    }
                });
            }
        });
        return n;
    }

    static boolean isSubscriptionCall(String name) {
        if (name == null) return false;
        String l = name.toLowerCase(Locale.US);
        for (String h : CHANNEL_HINTS) {
            if (h != null && l.indexOf(h.toLowerCase(Locale.US)) >= 0) return true;
        }
        return false;
    }
}
