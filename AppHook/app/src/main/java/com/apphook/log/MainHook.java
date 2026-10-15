package com.apphook.log;

import android.util.Log;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

/**
 * نقطة الدخول الحديثة (libxposed 101/102) — v4.0 WebView-Aware (Nuxt.js).
 *
 * <p>الاكتشاف الحاسم: الاشتراك صفحة Nuxt.js ({@code BillingSwitchConfirmDialog})
 * داخل {@code flutter_inappwebview} تتواصل عبر {@code FlutterBridge} —
 * لا PBL ولا RevenueCat ولا prefs محلية. لذا الطبقة الأولى هي
 * {@link WebViewHook} (التقاط الجسر + حقن JS + مراقبة API)، والبقية
 * احتياطي/تشخيص.</p>
 *
 * <p>يستخدم {@code getClassLoader()} من {@code PackageReadyParam}
 * (= lpparam.classLoader) — لا أسماء ثابتة. الهوك الحقيقي بعد فك الـ stub
 * (يدعم المضغوط/المحمي). بلا روت: LSPosed/LSPatch/NPatch/HKP.</p>
 */
public final class MainHook extends XposedModule {

    public MainHook() {
        super();
    }

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        SafeHook.attach(this);
        ModuleInfo.logStartup(this);
        try {
            log(Log.INFO, "AppHook", "v4.2 WebView body-patch loaded in: " + param.getProcessName()
                    + " | framework=" + getFrameworkName() + " " + getFrameworkVersion());
        } catch (Throwable ignored) {}
    }

    @Override
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {
        String pkg;
        try {
            pkg = param.getPackageName();
        } catch (Throwable t) {
            return;
        }
        if (!inScope(pkg)) return;
        SafeHook.log("package loaded (early): " + pkg + " — waiting onPackageReady for real ClassLoader");
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        String pkg;
        ClassLoader cl;
        try {
            pkg = param.getPackageName();
            cl = param.getClassLoader(); // <-- lpparam.classLoader الحقيقي (بعد فك الحماية)
        } catch (Throwable t) {
            SafeHook.logError("onPackageReady/params", t);
            return;
        }
        if (!inScope(pkg)) return;
        if (cl == null) {
            SafeHook.logSafe(pkg, "ClassLoader null — abort safely");
            return;
        }
        SafeHook.log("package ready: " + pkg + " | cl=" + cl);
        installAll(this, cl, pkg);
        // مسح الاكتشاف الديناميكي (DexFile عبر ApplicationInfo — بلا Context)
        try {
            DynamicScanner.scan(param.getApplicationInfo());
        } catch (Throwable t) {
            SafeHook.logError("DynamicScanner", t);
        }
    }

    static void installAll(XposedInterface fw, ClassLoader cl, String pkg) {
        // 0. WebView أولاً (مسار الاشتراك الحقيقي — Nuxt/FlutterBridge)
        try {
            WebViewHook.install(fw, cl);
        } catch (Throwable t) {
            SafeHook.logError("WebViewHook", t);
        }
        // 1. قناة Flutter (احتياطي/تشخيص)
        try {
            FlutterChannelHook.install(fw, cl);
        } catch (Throwable t) {
            SafeHook.logError("FlutterChannelHook", t);
        }
        // 2. SharedPreferences (يخزن Flutter هنا)
        try {
            PrefsHook.install(fw, cl);
        } catch (Throwable t) {
            SafeHook.logError("PrefsHook", t);
        }
        // 3. شبكة Native قبل وصولها لـ Flutter
        try {
            HttpHook.install(fw, cl);
        } catch (Throwable t) {
            SafeHook.logError("HttpHook", t);
        }
        // 4. طبقة JSON (Gson/org.json)
        try {
            JsonHook.install(fw, cl);
        } catch (Throwable t) {
            SafeHook.logError("JsonHook", t);
        }
        // 4.1 v3.1/v4.0: أحداث AppsFlyer (لحظة الشراء) — مراقبة
        try {
            AppsFlyerHook.install(fw, cl);
        } catch (Throwable t) {
            SafeHook.logError("AppsFlyerHook", t);
        }
        // 4.3 v3.1: جانب Dart HTTP عبر HttpURLConnection — مراقبة
        try {
            UrlConnectionHook.install(fw, cl);
        } catch (Throwable t) {
            SafeHook.logError("UrlConnectionHook", t);
        }
        // 5. Billing (احتياطي —native purchases)
        try {
            BillingHook.install(fw, cl);
        } catch (Throwable t) {
            SafeHook.logError("BillingHook", t);
        }
        try {
            RevenueCatHook.install(fw, cl);
        } catch (Throwable t) {
            SafeHook.logError("RevenueCatHook", t);
        }
        // 6. أعلام premium المحلية (مسح بصمة)
        try {
            FeatureHook.install(fw, cl, pkg);
        } catch (Throwable t) {
            SafeHook.logError("FeatureHook", t);
        }
        // 7. Integrity (مراقبة)
        try {
            IntegrityHook.install(fw, cl);
        } catch (Throwable t) {
            SafeHook.logError("IntegrityHook", t);
        }
        SafeHook.log("All v4.2 layers installed for: " + pkg);
    }

    /**
     * نطاق v3: builds موجهة لـ genspark (انظر scope.list).
     * للعودة للوضع الشامل: احذف scope.list وضع staticScope=false
     * وارجع الشرط القديم (كل شيء عدا blocklist النظام).
     */
    static boolean inScope(String pkg) {
        if (pkg == null) return false;
        if (pkg.equals("android")) return false;
        if (pkg.equals("com.android.systemui")) return false;
        if (pkg.equals("com.google.android.gms")) return false;
        if (pkg.equals("com.android.vending")) return false;
        if (pkg.equals(ModuleInfo.PACKAGE)) return false;
        return pkg.startsWith("ai.mainfunc.genspark");
    }
}
