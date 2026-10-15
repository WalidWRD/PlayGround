# SubscriptionAnalyzer ProGuard — entry points + commonly hooked SDKs
-keep class com.apphook.log.** { *; }
-keep class io.github.libxposed.api.XposedModule { *; }
-keepclasseswithmembers class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}
-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep class android.webkit.** { *; }
-keep class android.app.SharedPreferencesImpl { *; }
-keep class com.android.billingclient.** { *; }
-dontwarn com.android.billingclient.**
-keep class com.revenuecat.purchases.** { *; }
-dontwarn com.revenuecat.purchases.**
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class io.flutter.plugin.common.** { *; }
-keep class com.google.gson.** { *; }
-dontwarn io.flutter.plugin.common.**
