package com.genspark.updatekiller.hooks;

import android.app.AlertDialog;
import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.UpdateEventLogger;
import com.genspark.updatekiller.UxLog;
import com.genspark.updatekiller.hooks.DialogHook;
import de.robv.android.xposed.XC_MethodHook;
import java.util.Locale;

/* loaded from: classes.dex */
public final class DialogHook {
    private DialogHook() {
    }

    public static int install(ClassLoader classLoader) {
        if (!Config.get().dialogWatch) {
            UxLog.i("DialogHook: disabled");
            return 0;
        }
        Class<?> findClass = Reflect.findClass("android.app.AlertDialog", classLoader);
        if (findClass == null) {
            UxLog.i("DialogHook: AlertDialog not found");
            return 0;
        }
        int hookAllNamed = Reflect.hookAllNamed(findClass, "show", new AnonymousClass1()) + 0;
        UxLog.i("DialogHook: " + hookAllNamed + " hook(s)");
        return hookAllNamed;
    }

    /* renamed from: com.genspark.updatekiller.hooks.DialogHook$1, reason: invalid class name */
    static class AnonymousClass1 extends XC_MethodHook {
        AnonymousClass1() {
        }

        @Override // de.robv.android.xposed.XC_MethodHook
        protected void beforeHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("DialogHook.show", new Guard.Action() { // from class: com.genspark.updatekiller.hooks.DialogHook$1$$ExternalSyntheticLambda0
                @Override // com.genspark.updatekiller.Guard.Action
                public final void run() throws Throwable {
                    DialogHook.AnonymousClass1.lambda$beforeHookedMethod$0(methodHookParam);
                }
            });
        }

        static /* synthetic */ void lambda$beforeHookedMethod$0(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
            AlertDialog alertDialog = (AlertDialog) methodHookParam.thisObject;
            String dumpDialog = DialogHook.dumpDialog(alertDialog);
            if (dumpDialog != null && DialogHook.looksLikeUpdate(dumpDialog)) {
                try {
                    alertDialog.hide();
                    UpdateEventLogger.log("dialog", "HIDDEN update dialog: " + dumpDialog);
                    UxLog.i("DialogHook: hid update dialog");
                    methodHookParam.setResult(null);
                } catch (Throwable unused) {
                }
            }
            UxLog.i("DialogHook: dialog shown — " + dumpDialog);
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static boolean looksLikeUpdate(String str) {
        if (str == null) {
            return false;
        }
        String lowerCase = str.toLowerCase(Locale.ROOT);
        return lowerCase.contains("update") || lowerCase.contains("升级") || lowerCase.contains("تحديث") || lowerCase.contains("تحد") || lowerCase.contains("force") || lowerCase.contains("must update");
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String dumpDialog(AlertDialog alertDialog) {
        try {
            Object invoke = Reflect.invoke(alertDialog, null, "getMessage", new Object[0]);
            String str = "";
            String obj = invoke != null ? invoke.toString() : "";
            Object invoke2 = Reflect.invoke(alertDialog, null, "getTitle", new Object[0]);
            String obj2 = invoke2 == null ? "" : invoke2.toString();
            Object invoke3 = Reflect.invoke(alertDialog, null, "getNegativeButtonText", new Object[0]);
            String obj3 = invoke3 == null ? "" : invoke3.toString();
            Object invoke4 = Reflect.invoke(alertDialog, null, "getPositiveButtonText", new Object[0]);
            if (invoke4 != null) {
                str = invoke4.toString();
            }
            return (obj2 + " | " + obj + " | pos=" + str + " | neg=" + obj3).trim();
        } catch (Throwable unused) {
            return null;
        }
    }
}
