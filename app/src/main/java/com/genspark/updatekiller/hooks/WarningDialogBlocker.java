package com.genspark.updatekiller.hooks;

import android.app.AlertDialog;
import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.UpdateEventLogger;
import com.genspark.updatekiller.UxLog;
import com.genspark.updatekiller.hooks.WarningDialogBlocker;
import de.robv.android.xposed.XC_MethodHook;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/* loaded from: classes.dex */
public final class WarningDialogBlocker {
    private static final List<String> PAYMENT_KEYWORDS = Arrays.asList("subscribe", "subscription", "billing", "payment", "pay", "purchase", "checkout");

    private WarningDialogBlocker() {
    }

    public static int install(ClassLoader classLoader) {
        if (!Config.get().blockWarnings) {
            UxLog.i("WarningDialogBlocker: disabled");
            return 0;
        }
        Class<?> findClass = Reflect.findClass("android.app.AlertDialog", classLoader);
        if (findClass == null) {
            UxLog.i("WarningDialogBlocker: AlertDialog not found");
            return 0;
        }
        int hookAllNamed = Reflect.hookAllNamed(findClass, "show", new AnonymousClass1()) + 0;
        UxLog.i("WarningDialogBlocker: " + hookAllNamed + " hook(s)");
        return hookAllNamed;
    }

    /* renamed from: com.genspark.updatekiller.hooks.WarningDialogBlocker$1, reason: invalid class name */
    static class AnonymousClass1 extends XC_MethodHook {
        AnonymousClass1() {
        }

        @Override // de.robv.android.xposed.XC_MethodHook
        protected void beforeHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("WarningDialogBlocker.show", new Guard.Action() { // from class: com.genspark.updatekiller.hooks.WarningDialogBlocker$1$$ExternalSyntheticLambda0
                @Override // com.genspark.updatekiller.Guard.Action
                public final void run() throws Throwable {
                    WarningDialogBlocker.AnonymousClass1.lambda$beforeHookedMethod$0(methodHookParam);
                }
            });
        }

        static /* synthetic */ void lambda$beforeHookedMethod$0(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
            Object obj = methodHookParam.thisObject;
            if (obj instanceof AlertDialog) {
                AlertDialog alertDialog = (AlertDialog) obj;
                String dump = WarningDialogBlocker.dump(alertDialog);
                if (dump == null || dump.isEmpty()) {
                    return;
                }
                String lowerCase = dump.toLowerCase(Locale.ROOT);
                // v2.0.3: credit-exhaustion dialogs MUST be hidden (even though they
                // contain payment words like "subscribe" — old code returned early
                // on payment keywords and let these upsells through).
                if (lowerCase.contains("out of credits") || lowerCase.contains("insufficient credits")
                        || lowerCase.contains("credit exhausted") || lowerCase.contains("credits exhausted")
                        || lowerCase.contains("no credits") || lowerCase.contains("not enough credits")
                        || lowerCase.contains("quota exceeded") || lowerCase.contains("quota exhausted")
                        || lowerCase.contains("daily limit") || lowerCase.contains("limit reached")
                        || lowerCase.contains("الرصيد") || lowerCase.contains("رصيدك")
                        || lowerCase.contains("استنفذ") || lowerCase.contains("استنفد")
                        || lowerCase.contains("نفد رصيد") || lowerCase.contains("نفذ رصيد")
                        || lowerCase.contains("انتهى رصيد") || lowerCase.contains("رصيد غير كاف")) {
                    alertDialog.hide();
                    try {
                        alertDialog.dismiss();
                    } catch (Throwable ignored) {
                    }
                    UpdateEventLogger.log("dialog", "blocked credit-exhaustion dialog: " + dump);
                    UxLog.i("WarningDialogBlocker: hid credit dialog — " + dump);
                    return;
                }
                // v2.0.3: subscription upsell dialogs
                if (lowerCase.contains("subscribe now") || lowerCase.contains("go pro")
                        || lowerCase.contains("get premium") || lowerCase.contains("upgrade to pro")
                        || lowerCase.contains("premium required") || lowerCase.contains("pro required")
                        || lowerCase.contains("unlock premium") || lowerCase.contains("unlock pro")
                        || lowerCase.contains("اشترك الآن") || lowerCase.contains("اشترك في")) {
                    alertDialog.hide();
                    try {
                        alertDialog.dismiss();
                    } catch (Throwable ignored) {
                    }
                    UpdateEventLogger.log("dialog", "blocked subscription upsell: " + dump);
                    UxLog.i("WarningDialogBlocker: hid subs upsell — " + dump);
                    return;
                }
                Iterator it = WarningDialogBlocker.PAYMENT_KEYWORDS.iterator();
                while (it.hasNext()) {
                    if (lowerCase.contains((String) it.next())) {
                        return;
                    }
                }
                if (lowerCase.contains("announce") || lowerCase.contains("promo") || lowerCase.contains("الحملة") || lowerCase.contains("تنويه") || lowerCase.contains("إعلان") || lowerCase.contains("توصية") || lowerCase.contains("مرحبا") || lowerCase.contains("مرحبًا") || lowerCase.contains("rate") || lowerCase.contains("review") || lowerCase.contains("welcome") || lowerCase.contains("welcome back")) {
                    alertDialog.hide();
                    UpdateEventLogger.log("dialog", "blocked annoying dialog: " + dump);
                    UxLog.i("WarningDialogBlocker: hid annoying dialog — " + dump);
                }
            }
        }
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static String dump(AlertDialog alertDialog) {
        try {
            Object invoke = Reflect.invoke(alertDialog, null, "getMessage", new Object[0]);
            String obj = invoke != null ? invoke.toString() : "";
            Object invoke2 = Reflect.invoke(alertDialog, null, "getTitle", new Object[0]);
            return ((invoke2 != null ? invoke2.toString() : "") + " | " + obj).trim();
        } catch (Throwable unused) {
            return null;
        }
    }
}
