package com.genspark.updatekiller.hooks;

import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.UxLog;
import com.genspark.updatekiller.hooks.PlayCoreHook;
import de.robv.android.xposed.XC_MethodHook;

/* loaded from: classes.dex */
public final class PlayCoreHook {
    private PlayCoreHook() {
    }

    public static int install(ClassLoader classLoader) {
        if (!Config.get().playCore) {
            UxLog.i("PlayCoreHook: disabled");
            return 0;
        }
        int hookBySignature = Reflect.hookBySignature(Reflect.findClass("com.google.android.play.core.appupdate.AppUpdateManager", classLoader), new String[]{"checkUpdate", "isUpdateAvailable"}, null, new AnonymousClass1()) + 0 + Reflect.hookBySignature(Reflect.findClass("com.google.android.play.core.appupdate.AppUpdateManager", classLoader), new String[]{"startUpdateFlow"}, null, new AnonymousClass2());
        Class<?> findClass = Reflect.findClass("com.google.android.play.core.tasks.Tasks", classLoader);
        if (findClass != null) {
            hookBySignature += Reflect.hookAllNamed(findClass, "await", new AnonymousClass3());
        }
        UxLog.i("PlayCoreHook: " + hookBySignature + " hook(s)");
        return hookBySignature;
    }

    /* renamed from: com.genspark.updatekiller.hooks.PlayCoreHook$1, reason: invalid class name */
    static class AnonymousClass1 extends XC_MethodHook {
        AnonymousClass1() {
        }

        @Override // de.robv.android.xposed.XC_MethodHook
        protected void afterHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("PlayCoreHook.check/isAvailable", new Guard.Action() { // from class: com.genspark.updatekiller.hooks.PlayCoreHook$1$$ExternalSyntheticLambda0
                @Override // com.genspark.updatekiller.Guard.Action
                public final void run() throws Throwable {
                    PlayCoreHook.AnonymousClass1.lambda$afterHookedMethod$0(methodHookParam);
                }
            });
        }

        static /* synthetic */ void lambda$afterHookedMethod$0(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
            methodHookParam.setResult(null);
            UxLog.i("PlayCoreHook: blocked check for update");
        }
    }

    /* renamed from: com.genspark.updatekiller.hooks.PlayCoreHook$2, reason: invalid class name */
    static class AnonymousClass2 extends XC_MethodHook {
        AnonymousClass2() {
        }

        @Override // de.robv.android.xposed.XC_MethodHook
        protected void beforeHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("PlayCoreHook.startUpdateFlow", new Guard.Action() { // from class: com.genspark.updatekiller.hooks.PlayCoreHook$2$$ExternalSyntheticLambda0
                @Override // com.genspark.updatekiller.Guard.Action
                public final void run() throws Throwable {
                    PlayCoreHook.AnonymousClass2.lambda$beforeHookedMethod$0(methodHookParam);
                }
            });
        }

        static /* synthetic */ void lambda$beforeHookedMethod$0(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
            methodHookParam.setResult(null);
            UxLog.i("PlayCoreHook: blocked startUpdateFlow");
        }
    }

    /* renamed from: com.genspark.updatekiller.hooks.PlayCoreHook$3, reason: invalid class name */
    static class AnonymousClass3 extends XC_MethodHook {
        static /* synthetic */ void lambda$afterHookedMethod$0() throws Throwable {
        }

        AnonymousClass3() {
        }

        @Override // de.robv.android.xposed.XC_MethodHook
        protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("PlayCoreHook.Tasks.await", new Guard.Action() { // from class: com.genspark.updatekiller.hooks.PlayCoreHook$3$$ExternalSyntheticLambda0
                @Override // com.genspark.updatekiller.Guard.Action
                public final void run() throws Throwable {
                    PlayCoreHook.AnonymousClass3.lambda$afterHookedMethod$0();
                }
            });
        }
    }
}
