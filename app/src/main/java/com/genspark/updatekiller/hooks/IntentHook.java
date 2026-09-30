package com.genspark.updatekiller.hooks;

import android.content.Intent;
import android.net.Uri;
import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.UpdateEventLogger;
import com.genspark.updatekiller.UxLog;
import com.genspark.updatekiller.hooks.IntentHook;
import de.robv.android.xposed.XC_MethodHook;
import java.util.Locale;

/* loaded from: classes.dex */
public final class IntentHook {
    private IntentHook() {
    }

    public static int install(ClassLoader classLoader) {
        if (!Config.get().blockStoreIntent) {
            UxLog.i("IntentHook: disabled");
            return 0;
        }
        Class<?> findClass = Reflect.findClass("android.content.ContextWrapper", classLoader);
        int hookAllNamed = findClass != null ? 0 + Reflect.hookAllNamed(findClass, "startActivity", new AnonymousClass1()) : 0;
        UxLog.i("IntentHook: " + hookAllNamed + " hook(s)");
        return hookAllNamed;
    }

    /* renamed from: com.genspark.updatekiller.hooks.IntentHook$1, reason: invalid class name */
    static class AnonymousClass1 extends XC_MethodHook {
        AnonymousClass1() {
        }

        @Override // de.robv.android.xposed.XC_MethodHook
        protected void beforeHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("IntentHook.startActivity", new Guard.Action() { // from class: com.genspark.updatekiller.hooks.IntentHook$1$$ExternalSyntheticLambda0
                @Override // com.genspark.updatekiller.Guard.Action
                public final void run() throws Throwable {
                    IntentHook.AnonymousClass1.lambda$beforeHookedMethod$0(methodHookParam);
                }
            });
        }

        static /* synthetic */ void lambda$beforeHookedMethod$0(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
            Intent intent;
            Uri data;
            String host;
            if (methodHookParam.args == null || methodHookParam.args.length < 1) {
                return;
            }
            Object obj = methodHookParam.args[0];
            if (!(obj instanceof Intent) || (data = (intent = (Intent) obj).getData()) == null || (host = data.getHost()) == null) {
                return;
            }
            String lowerCase = host.toLowerCase(Locale.ROOT);
            if (lowerCase.contains("play.google") || lowerCase.contains("market.android")) {
                intent.setData(Uri.parse(Config.get().storeRedirectUrl));
                intent.setPackage(null);
                UpdateEventLogger.log("intent", "store redirect → " + Config.get().storeRedirectUrl);
                UxLog.i("IntentHook: redirected Play Store → " + Config.get().storeRedirectUrl);
            }
        }
    }
}
