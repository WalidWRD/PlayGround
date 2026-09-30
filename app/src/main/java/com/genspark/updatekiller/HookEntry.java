package com.genspark.updatekiller;

import com.genspark.updatekiller.hooks.AdsBlocker;
import com.genspark.updatekiller.hooks.BlutterTrace;
import com.genspark.updatekiller.hooks.ChannelHook;
import com.genspark.updatekiller.hooks.DialogHook;
import com.genspark.updatekiller.hooks.FlutterDiscoveryHook;
import com.genspark.updatekiller.hooks.HttpHook;
import com.genspark.updatekiller.hooks.IntentHook;
import com.genspark.updatekiller.hooks.NativeHook;
import com.genspark.updatekiller.hooks.NativeProbeHook;
import com.genspark.updatekiller.hooks.PackageManagerDiscoveryHook;
import com.genspark.updatekiller.hooks.PlayCoreHook;
import com.genspark.updatekiller.hooks.PrefsHook;
import com.genspark.updatekiller.hooks.SubscriptionHook;
import com.genspark.updatekiller.hooks.UniversalGateHook;
import com.genspark.updatekiller.hooks.VersionSpoofHook;
import com.genspark.updatekiller.hooks.WebViewHook;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * نقطة الدخول v2.0.0 — تعمل مع LSPatch / NPatch / HKP-Patch / LSPosed بلا روت.
 *
 * مبدأ التصميم:
 *  1) لا أسماء ثابتة: كل ربط عبر lpparam.classLoader + فهرس DEX + مطابقة بنيوية.
 *  2) تثبيت على مرحلتين: خطّافات «أساسية» فورية + مهام «اكتشاف ديناميكي» تُنفَّذ في
 *     مرورات مُؤجَّلة وتُعاد عند تحميل كل فئة مُهمّة (للحزم المضغوطة/المحمية).
 *  3) تقرير ذاتي JSON في النهاية لكل المكوّنات (بدل إعادة التحليل يدويًا).
 */
public class HookEntry implements IXposedHookLoadPackage {

    private static volatile boolean installed = false;
    private static volatile ClassLoader CL;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) {
        Guard.run("HookEntry", new Guard.Action() {
            @Override public void run() {
                if (lpparam == null) return;
                final ClassLoader cl = lpparam.classLoader;
                CL = cl;
                final String pkg = lpparam.packageName;
                if (!isTarget(pkg, cl)) return;

                final Config cfg = Config.get();
                if (!cfg.enabled) {
                    UxLog.i("module disabled by config — nothing installed");
                    return;
                }
                synchronized (HookEntry.class) {
                    if (installed) return;
                    installed = true;
                }

                banner(pkg);

                // 1) فهرس أسماء الفئات + رصد تحميل الفئات (أساس كل الاكتشاف الديناميكي)
                if (cfg.classScan) Discovery.start(cl);
                else UxLog.i("Discovery: classScan disabled by config");

                // 2) جدولة المهام: أساسية فورية + ديناميكية مؤجّلة
                registerTasks(cfg);

                DeferredInstaller.start(cl, new Runnable() {
                    @Override public void run() {
                        finish(pkg);
                    }
                });
                DeferredInstaller.kick("boot");

                // 3) جسر الاشتراك (v2.0.1: نجاح محلي فوري عند ForceActive + خادم اختياري)
                SubscriptionBridge.refresh();
            }
        });
    }

    private static void registerTasks(final Config cfg) {
        // ── الأساسية: تعمل في المرور الأول (سلوك v1.x المحفوظ) ──
        DeferredInstaller.add(new DeferredInstaller.Task() {
            @Override public String name() { return "core"; }
            @Override public int run(ClassLoader cl) {
                int n = 0;
                n += VersionSpoofHook.install(cl);
                n += ChannelHook.install(cl);
                n += IntentHook.install(cl);
                n += WebViewHook.install(cl);
                n += HttpHook.install(cl);
                n += PrefsHook.install(cl);
                n += DialogHook.install(cl);
                n += PlayCoreHook.install(cl);
                n += NativeHook.install(cl);
                n += AdsBlocker.install(cl);
                n += BlutterTrace.install(cl);
                return n;
            }
        });

        // ── الديناميكية: تنتظر الفهرس/تحميل الفئات وتُعاد تلقائيًا ──
        if (cfg.pmDiscovery) {
            DeferredInstaller.add(new DeferredInstaller.Task() {
                @Override public String name() { return "pm-discovery"; }
                @Override public int run(ClassLoader cl) { return PackageManagerDiscoveryHook.install(cl); }
            });
        }
        if (cfg.flutterDiscovery) {
            DeferredInstaller.add(new DeferredInstaller.Task() {
                @Override public String name() { return "flutter-discovery"; }
                @Override public int run(ClassLoader cl) { return FlutterDiscoveryHook.install(cl); }
            });
        }
        if (cfg.universalGate) {
            DeferredInstaller.add(new DeferredInstaller.Task() {
                @Override public String name() { return "universal-gate"; }
                @Override public int run(ClassLoader cl) { return UniversalGateHook.install(cl); }
            });
        }
        if (cfg.nativeScan) {
            DeferredInstaller.add(new DeferredInstaller.Task() {
                @Override public String name() { return "native-scan"; }
                @Override public int run(ClassLoader cl) { return NativeProbeHook.install(cl); }
            });
        }
        if (cfg.subscriptionBridge) {
            DeferredInstaller.add(new DeferredInstaller.Task() {
                @Override public String name() { return "subscription"; }
                @Override public int run(ClassLoader cl) { return SubscriptionHook.install(cl); }
            });
        }
    }

    private static void finish(String pkg) {
        Guard.run("HookEntry.finish", new Guard.Action() {
            @Override public void run() {
                int total = SelfTest.totalHooks();
                String pending = DeferredInstaller.pendingList();
                int failures = Guard.failures();
                boolean kill = Guard.isTripped();

                String verdict;
                if (kill) verdict = "KILL_SWITCH";
                else if (total > 0 && pending.length() == 0) verdict = "OK";
                else if (total > 0) verdict = "PARTIAL";
                else verdict = "NO_HOOKS";

                SelfTest.finish(verdict, "pending=" + pending
                        + "; dexScanned=" + Discovery.isDexScanned()
                        + "; names=" + Discovery.names());

                String summary = "ALL DONE — hooks=" + total
                        + " failures=" + failures
                        + " killSwitch=" + kill
                        + " verdict=" + verdict
                        + " pending=[" + pending + "]"
                        + " (target " + pkg + ")";
                UxLog.i(summary);
                UxLog.i("ENV: " + EnvInfo.describe(null));
                UpdateEventLogger.log("session", summary);
                SelfTest.write(CL, pkg);
            }
        });
    }

    private static void banner(String pkg) {
        try {
            UxLog.i("┌───────────────────────────────────────────────");
            for (String line : BuildInfo.describe().split("\n")) UxLog.i("│ " + line);
            UxLog.i("│ loaded in: " + pkg);
            UxLog.i("└───────────────────────────────────────────────");
        } catch (Throwable ignored) { }
    }

    private static boolean isTarget(String pkg, ClassLoader cl) {
        if (pkg != null) {
            for (String t : BuildInfo.PACKAGE_NAMES) if (t.equals(pkg)) return true;
            for (String t : Config.get().packages) if (t != null && t.equals(pkg)) return true;
            if (pkg.startsWith("ai.mainfunc.genspark")) return true;
        }
        // احتياط: بعض البيئات الافتراضية تُعيد اسم حزمة مختلفًا — نتحقق من الفئة الرئيسية
        return Reflect.hasClass("ai.mainfunc.genspark.MainActivity", cl);
    }
}
