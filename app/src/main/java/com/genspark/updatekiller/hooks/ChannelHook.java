package com.genspark.updatekiller.hooks;

import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.UpdateEventLogger;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.UxLog;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;

/**
 * رصد/اعتراض قنوات Flutter (MethodChannel.invokeMethod) — تشخيصي وأمان كامل:
 * لا يستدعي setResult على دالة تُعيد void (لتجنّب أي كسر)، ويسجّل فقط،
 * مع إمكانية الاعتراض عندما تكون الدالة غير void.
 */
public final class ChannelHook {

    private static final String[] WATCH = {
        "openStoreListing", "requestReview", "checkUpdate", "forceUpdate",
        "getAppVersion", "appUpdate", "updateNow", "installUpdate"
    };

    private ChannelHook() { }

    public static int install(ClassLoader cl) {
        Class<?> mc = Reflect.findClass("io.flutter.plugin.common.MethodChannel", cl);
        if (mc == null) { UxLog.i("ChannelHook: Flutter MethodChannel not present"); return 0; }
        int n = Reflect.hookAllNamed(mc, "invokeMethod", new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                Guard.run("ChannelHook.invokeMethod", () -> {
                    if (p.args == null || p.args.length == 0) return;
                    Object a0 = p.args[0];
                    if (!(a0 instanceof String)) return;
                    String name = (String) a0;
                    for (String w : WATCH) {
                        if (w.equalsIgnoreCase(name)) {
                            UxLog.i("ChannelHook: observed channel call '" + name + "'");
                            if (isDialogish(name)) UpdateEventLogger.log("channel", "invokeMethod(\"" + name + "\") — مؤشر ظهور حوار/بوابة تحديث");
                            Method m = (p.method instanceof Method) ? (Method) p.method : null;
                            if (m != null && m.getReturnType() != void.class) p.setResult(null);
                            return;
                        }
                    }
                });
            }
        });
        UxLog.i("ChannelHook: " + n + " hook(s) on MethodChannel.invokeMethod");
        return n;
    }

    /** أسماء القنوات المرتبطة بظهور حوار تحديث (تُسجَّل في ملف الأحداث) — يستثني getAppVersion/requestReview لتقليل الضجيج. */
    private static final String[] DIALOGISH = {
        "openStoreListing", "checkUpdate", "forceUpdate", "appUpdate", "updateNow", "installUpdate"
    };

    private static boolean isDialogish(String name) {
        for (String d : DIALOGISH) if (d.equalsIgnoreCase(name)) return true;
        return false;
    }
}
