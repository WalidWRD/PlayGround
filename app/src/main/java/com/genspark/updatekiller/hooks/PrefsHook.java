package com.genspark.updatekiller.hooks;

import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.UxLog;

import java.lang.reflect.Method;

import de.robv.android.xposed.XC_MethodHook;

/**
 * يمنع أن يُفعّل حوار التحديث من قيمة مخزّنة مؤقتًا (shared_preferences).
 * يعدّل فقط المفاتيح المستهدفة (مثل flutter.forceUpgrade / minAppVersionCode)
 * ويترك كل ما عداها دون أي تغيير.
 */
public final class PrefsHook {

    private PrefsHook() { }

    public static int install(ClassLoader cl) {
        if (!Config.get().prefsHook) { UxLog.i("PrefsHook: disabled by config"); return 0; }
        final Config cfg = Config.get();
        int n = 0;

        Class<?> impl = Reflect.findClass("android.app.SharedPreferencesImpl", cl);
        Class<?> ed   = Reflect.findClass("android.app.SharedPreferencesImpl$EditorImpl", cl);
        if (impl == null) { UxLog.i("PrefsHook: SharedPreferencesImpl not found"); return 0; }

        if (ed != null) {
            n += Reflect.hookAllNamed(ed, "putBoolean", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    Guard.run("PrefsHook.putBoolean", () -> {
                        if (p.args != null && p.args.length == 2 && p.args[0] instanceof String
                                && cfg.hasKey((String) p.args[0]) && Boolean.TRUE.equals(p.args[1])) {
                            p.args[1] = Boolean.FALSE;
                            UxLog.d("PrefsHook: " + p.args[0] + " true→false");
                        }
                    });
                }
            });
            n += Reflect.hookAllNamed(ed, "putInt", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    Guard.run("PrefsHook.putInt", () -> {
                        if (p.args != null && p.args.length == 2 && p.args[0] instanceof String
                                && cfg.hasKey((String) p.args[0])) {
                            p.args[1] = Integer.valueOf(0);
                            UxLog.d("PrefsHook: " + p.args[0] + " →0");
                        }
                    });
                }
            });
            n += Reflect.hookAllNamed(ed, "putLong", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    Guard.run("PrefsHook.putLong", () -> {
                        if (p.args != null && p.args.length == 2 && p.args[0] instanceof String
                                && cfg.hasKey((String) p.args[0])) {
                            p.args[1] = Long.valueOf(0L);
                            UxLog.d("PrefsHook: " + p.args[0] + " →0");
                        }
                    });
                }
            });
            n += Reflect.hookAllNamed(ed, "putString", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    Guard.run("PrefsHook.putString", () -> {
                        if (p.args != null && p.args.length == 2 && p.args[0] instanceof String
                                && cfg.hasKey((String) p.args[0])) {
                            p.args[1] = "";
                            UxLog.d("PrefsHook: " + p.args[0] + " →\"\"");
                        }
                    });
                }
            });
        }

        n += Reflect.hookAllNamed(impl, "getBoolean", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Guard.run("PrefsHook.getBoolean", () -> {
                    if (p.args != null && p.args.length >= 1 && p.args[0] instanceof String
                            && cfg.hasKey((String) p.args[0]) && Boolean.TRUE.equals(p.getResult())) {
                        p.setResult(Boolean.FALSE);
                        UxLog.d("PrefsHook: read " + p.args[0] + " forced false");
                    }
                });
            }
        });
        n += Reflect.hookAllNamed(impl, "getInt", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Guard.run("PrefsHook.getInt", () -> {
                    if (p.args != null && p.args.length >= 1 && p.args[0] instanceof String
                            && cfg.hasKey((String) p.args[0])) p.setResult(Integer.valueOf(0));
                });
            }
        });
        n += Reflect.hookAllNamed(impl, "getLong", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Guard.run("PrefsHook.getLong", () -> {
                    if (p.args != null && p.args.length >= 1 && p.args[0] instanceof String
                            && cfg.hasKey((String) p.args[0])) p.setResult(Long.valueOf(0L));
                });
            }
        });
        n += Reflect.hookAllNamed(impl, "getString", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Guard.run("PrefsHook.getString", () -> {
                    if (p.args != null && p.args.length >= 1 && p.args[0] instanceof String
                            && cfg.hasKey((String) p.args[0]) && p.getResult() != null) p.setResult("");
                });
            }
        });

        UxLog.i("PrefsHook: " + n + " hook(s)");
        return n;
    }
}
