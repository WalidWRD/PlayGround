package com.genspark.updatekiller.hooks;

import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.UpdateEventLogger;
import com.genspark.updatekiller.UxLog;
import de.robv.android.xposed.XC_MethodHook;
import java.lang.reflect.Method;
import java.util.Locale;

/**
 * v2.0.1: rewritten so it compiles (v2.0.0 dex used an uninitialized
 * register pattern jadx could not reconstruct). Behaviour identical.
 */
public final class PrefsHook {
    static final String[] TARGET_PUT_KEYS = {"first_run", "firstRun", "first_run_done", "update_dialog_shown", "updateDialogShown", "last_force_update", "lastForceUpdate", "has_seen_update_nag", "hasSeenUpdateNag", "update_nag_count", "updateNagCount", "must_update", "mustUpdate", "app_should_update"};
    static final String[] TARGET_GET_KEYS = {"force_upgrade", "forceUpgrade", "min_app_version_code", "minAppVersionCode"};

    private PrefsHook() {
    }

    public static int install(ClassLoader classLoader) {
        if (!Config.get().prefsHook) {
            UxLog.i("PrefsHook: disabled");
            return 0;
        }
        Class<?> editor = Reflect.findClass("android.app.SharedPreferencesImpl$EditorImpl", classLoader);
        if (editor == null) {
            UxLog.i("PrefsHook: EditorImpl not found");
            return 0;
        }
        int hooks = 0;
        Method putBoolean = null;
        try {
            putBoolean = editor.getDeclaredMethod("putBoolean", String.class, Boolean.TYPE);
        } catch (Throwable th) {
            Guard.record("PrefsHook.putBoolean-reflect", th);
        }
        if (putBoolean != null) {
            if (Reflect.hook(putBoolean, new AnonymousClass1())) {
                hooks++;
            }
        }
        Method putString = null;
        try {
            putString = editor.getDeclaredMethod("putString", String.class, String.class);
        } catch (Throwable th) {
            Guard.record("PrefsHook.putString-reflect", th);
        }
        if (putString != null) {
            if (Reflect.hook(putString, new AnonymousClass2())) {
                hooks++;
            }
        }
        UxLog.i("PrefsHook: " + hooks + " hook(s)");
        return hooks;
    }

    /* renamed from: com.genspark.updatekiller.hooks.PrefsHook$1 */
    static class AnonymousClass1 extends XC_MethodHook {
        AnonymousClass1() {
        }

        @Override
        protected void beforeHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("PrefsHook.putBoolean", new Guard.Action() {
                @Override
                public void run() throws Throwable {
                    AnonymousClass1.lambda$beforeHookedMethod$0(methodHookParam);
                }
            });
        }

        static void lambda$beforeHookedMethod$0(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
            if (methodHookParam.args == null || methodHookParam.args.length < 2) {
                return;
            }
            String valueOf = String.valueOf(methodHookParam.args[0]);
            if (valueOf == null) {
                return;
            }
            for (String str : PrefsHook.TARGET_PUT_KEYS) {
                if (valueOf.toLowerCase(Locale.ROOT).contains(str.toLowerCase(Locale.ROOT))) {
                    methodHookParam.args[1] = Boolean.FALSE;
                    UpdateEventLogger.log("prefs", "forced '" + valueOf + "'=false");
                }
            }
        }
    }

    /* renamed from: com.genspark.updatekiller.hooks.PrefsHook$2 */
    static class AnonymousClass2 extends XC_MethodHook {
        AnonymousClass2() {
        }

        @Override
        protected void beforeHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("PrefsHook.putString", new Guard.Action() {
                @Override
                public void run() throws Throwable {
                    AnonymousClass2.lambda$beforeHookedMethod$0(methodHookParam);
                }
            });
        }

        static void lambda$beforeHookedMethod$0(XC_MethodHook.MethodHookParam methodHookParam) throws Throwable {
            String valueOf;
            if (methodHookParam.args == null || methodHookParam.args.length < 2 || (valueOf = String.valueOf(methodHookParam.args[0])) == null) {
                return;
            }
            String lowerCase = valueOf.toLowerCase(Locale.ROOT);
            if (lowerCase.contains("last_force") || lowerCase.contains("update_nag") || lowerCase.contains("force_msg")) {
                methodHookParam.args[1] = "";
                UpdateEventLogger.log("prefs", "forced '" + valueOf + "'=''");
            }
        }
    }
}
