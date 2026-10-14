# AppHook ProGuard — يحافظ على نقاط الدخول وفئات الفوترة
-keep class com.apphook.log.** { *; }
-keep class io.github.libxposed.api.XposedModule { *; }
-keepclasseswithmembers class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
-adaptresourcefilecontents META-INF/xposed/java_init.list
# فئات مستقرة عبر R8 (Billing/RevenueCat/OkHttp/Integrity/Flutter/JSON)
-keep class com.android.billingclient.api.** { *; }
-keep class com.revenuecat.purchases.** { *; }
-keep class okhttp3.** { *; }
-keep class com.google.android.play.core.integrity.** { *; }
-keep class io.flutter.plugin.common.** { *; }
-keep class com.google.gson.** { *; }
-dontwarn io.flutter.plugin.common.**
