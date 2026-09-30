package com.genspark.updatekiller.hooks;

import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.UxLog;
import com.genspark.updatekiller.hooks.AntiCrash;
import de.robv.android.xposed.XC_MethodHook;

/* loaded from: classes.dex */
public final class AntiCrash {
    private AntiCrash() {
    }

    public static int install(ClassLoader classLoader) {
        if (!Config.get().antiCrash) {
            UxLog.i("AntiCrash: disabled");
            return 0;
        }
        Class<?> findClass = Reflect.findClass("android.app.Application", classLoader);
        int hookAllNamed = findClass != null ? Reflect.hookAllNamed(findClass, "onLowMemory", new AnonymousClass1()) + 0 + Reflect.hookAllNamed(findClass, "onTrimMemory", new AnonymousClass2()) : 0;
        Class<?> findClass2 = Reflect.findClass("java.lang.Thread", classLoader);
        if (findClass2 != null) {
            try {
                hookAllNamed += Reflect.hookAllNamed(findClass2, "setDefaultUncaughtExceptionHandler", new XC_MethodHook() { // from class: com.genspark.updatekiller.hooks.AntiCrash.3
                    @Override // de.robv.android.xposed.XC_MethodHook
                    protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    }
                });
            } catch (Throwable unused) {
            }
        }
        Class<?> findClass3 = Reflect.findClass("android.app.Activity", classLoader);
        if (findClass3 != null) {
            hookAllNamed += Reflect.hookAllNamed(findClass3, "onCreate", new AnonymousClass4());
        }
        UxLog.i("AntiCrash: " + hookAllNamed + " hook(s) — safety layer active");
        return hookAllNamed;
    }

    /* renamed from: com.genspark.updatekiller.hooks.AntiCrash$1, reason: invalid class name */
    static class AnonymousClass1 extends XC_MethodHook {
        AnonymousClass1() {
        }

        @Override // de.robv.android.xposed.XC_MethodHook
        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("AntiCrash.onLowMemory", new Guard.Action() { // from class: com.genspark.updatekiller.hooks.AntiCrash$1$$ExternalSyntheticLambda0
                @Override // com.genspark.updatekiller.Guard.Action
                public final void run() throws Throwable {
                    UxLog.w("AntiCrash: low-memory signal received in target app");
                }
            });
        }
    }

    /* renamed from: com.genspark.updatekiller.hooks.AntiCrash$2, reason: invalid class name */
    static class AnonymousClass2 extends XC_MethodHook {
        AnonymousClass2() {
        }

        @Override // de.robv.android.xposed.XC_MethodHook
        protected void beforeHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("AntiCrash.onTrimMemory", new Guard.Action() { // from class: com.genspark.updatekiller.hooks.AntiCrash$2$$ExternalSyntheticLambda0
                @Override // com.genspark.updatekiller.Guard.Action
                public final void run() throws Throwable {
                    AntiCrash.AnonymousClass2.lambda$beforeHookedMethod$0(methodHookParam);
                }
            });
        }

        static /* synthetic */ void lambda$beforeHookedMethod$0(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
            if (methodHookParam.args == null || methodHookParam.args.length < 1) {
                return;
            }
            int intValue = methodHookParam.args[0] instanceof Integer ? ((Integer) methodHookParam.args[0]).intValue() : -1;
            if (intValue >= 80) {
                UxLog.w("AntiCrash: critical trimMemory level " + intValue);
            }
        }
    }

    /* renamed from: com.genspark.updatekiller.hooks.AntiCrash$4, reason: invalid class name */
    static class AnonymousClass4 extends XC_MethodHook {
        AnonymousClass4() {
        }

        @Override // de.robv.android.xposed.XC_MethodHook
        protected void beforeHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("AntiCrash.onCreate", new Guard.Action() { // from class: com.genspark.updatekiller.hooks.AntiCrash$4$$ExternalSyntheticLambda0
                @Override // com.genspark.updatekiller.Guard.Action
                public final void run() throws Throwable {
                    AntiCrash.AnonymousClass4.lambda$beforeHookedMethod$0(methodHookParam);
                }
            });
        }

        static /* synthetic */ void lambda$beforeHookedMethod$0(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
            if (methodHookParam.thisObject != null) {
                UxLog.d("AntiCrash: Activity created: " + methodHookParam.thisObject.getClass().getName());
            }
        }
    }
}
