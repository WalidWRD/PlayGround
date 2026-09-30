package com.genspark.updatekiller.hooks;

import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.UpdateEventLogger;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.UxLog;
import com.genspark.updatekiller.json.JsonNeutralizer;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;

/**
 * تعطيل حقول الفرض داخل أي ردّ يمرّ عبر طبقة Java (okhttp / okio).
 * ملاحظة: طلبات Flutter (dart:io) لا تمرّ من Java؛ لتلك الحالة استخدم
 * وكيل MITM أو آلية تزوير الإصدار (VersionSpoofHook) وهي كافية عمليًا.
 */
public final class HttpHook {

    private HttpHook() { }

    public static int install(ClassLoader cl) {
        if (!Config.get().neutralizeJson) { UxLog.i("HttpHook: disabled by config"); return 0; }
        int n = 0;

        Class<?> rb = Reflect.findClass("okhttp3.ResponseBody", cl);
        if (rb != null) {
            n += Reflect.hookAllNamed(rb, "string", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Guard.run("HttpHook.ResponseBody.string", () -> rewrite(p));
                }
            });
        }
        Class<?> buf = Reflect.findClass("okio.Buffer", cl);
        if (buf != null) {
            n += Reflect.hookAllNamed(buf, "readUtf8", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    Guard.run("HttpHook.Buffer.readUtf8", () -> rewrite(p));
                }
            });
        }
        UxLog.i("HttpHook: " + n + " hook(s)");
        return n;
    }

    private static void rewrite(MethodHookParam p) {
        Object r = p.getResult();
        if (!(r instanceof String)) return;
        String s = (String) r;
        String out = JsonNeutralizer.applyToText(s);
        if (JsonNeutralizer.changedText(s, out)) {
            p.setResult(out);
            UxLog.i("HttpHook: neutralized update fields (chars " + s.length() + ")");
            UpdateEventLogger.log("http", "update-config response neutralized (chars " + s.length() + ") — الخادم كان سيُظهر حوار التحديث");
        }
    }
}
