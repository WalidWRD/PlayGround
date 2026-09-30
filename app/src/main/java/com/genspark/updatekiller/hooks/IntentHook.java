package com.genspark.updatekiller.hooks;

import android.content.Intent;
import android.net.Uri;

import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.UpdateEventLogger;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.UxLog;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;

/**
 * يمنع فتح صفحة التطبيق في متجر Play (زر «حدّث الآن») بأمان تام.
 *
 * كيف نتجنّب الكراش: دوال startActivity/startActivityForResult تُعيد void،
 * ولا يمكن «إلغاء» دالة void بـsetResult في بعض الأطر. لذلك لا نُلغي الاستدعاء،
 * بل نُعدّل كائن Intent نفسه في مكانه (Component/Package/Action/Data) ونُبقي
 * كل الأعلام (flags) كما هي — فيتحوّل الهدف إلى رابط ويب عادي بدل المتجر،
 * بلا ActivityNotFoundException وبلا أي استثناء يصل إلى التطبيق.
 */
public final class IntentHook {

    private static final String[] CLASSES = {
        "android.app.Activity",
        "android.content.ContextWrapper",
        "android.app.ContextImpl"
    };

    private static String safeUrl() {
        String u = Config.get().storeRedirectUrl;
        return (u != null && u.length() > 0) ? u : "https://www.genspark.ai/";
    }

    private IntentHook() { }

    public static int install(ClassLoader cl) {
        if (!Config.get().blockStoreIntent) { UxLog.i("IntentHook: disabled by config"); return 0; }
        int n = 0;
        for (String cn : CLASSES) {
            Class<?> c = Reflect.findClass(cn, cl);
            if (c == null) continue;
            n += Reflect.hookBySignature(c, new String[]{"startActivity", "startActivityForResult"},
                    new String[]{"android.content.Intent"}, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    Guard.run("IntentHook", () -> redirect(p));
                }
            });
        }
        UxLog.i("IntentHook: " + n + " hook(s) — store intents are redirected, not cancelled");
        return n;
    }

    private static void redirect(MethodHookParam p) {
        if (p.args == null || p.args.length == 0) return;
        Object a0 = p.args[0];
        if (!(a0 instanceof Intent)) return;
        Intent it = (Intent) a0;
        if (!targetsStore(it)) return;

        String before = String.valueOf(it.getData());
        try {
            // تحويل الهدف إلى رابط ويب عادي مع الحفاظ على الأعلام الأصلية
            it.setComponent(null);
            it.setPackage(null);
            it.setAction(Intent.ACTION_VIEW);
            it.setData(Uri.parse(safeUrl()));
            UxLog.i("IntentHook: store intent redirected → " + safeUrl() + "  (was: " + before + ")");
            UpdateEventLogger.log("intent", "store intent redirected (was: " + before + " → " + safeUrl() + ") — دليل ظهور زر التحديث");
        } catch (Throwable t) {
            UxLog.w("IntentHook: redirect failed, leaving intent untouched (" + t + ")");
        }
    }

    private static boolean targetsStore(Intent it) {
        try {
            String data = (it.getData() != null) ? it.getData().toString() : "";
            String act  = String.valueOf(it.getAction());
            String pkg  = String.valueOf(it.getPackage());
            if (data.contains("market://")) return true;
            if (data.contains("play.google.com/store")) return true;
            if (data.contains("apps/details?id=")) return true;
            if (data.startsWith("market")) return true;
            if ("com.android.vending".equals(it.getPackage())) return true;
            if (act.contains("com.android.vending")) return true;
            if (pkg.contains("com.android.vending")) return true;
            return false;
        } catch (Throwable t) {
            return false;
        }
    }
}
