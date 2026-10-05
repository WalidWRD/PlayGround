# Keep Xposed entry (referenced from assets/xposed_init by name).
-keep class com.ashnapatch.xposed.HookEntry { *; }
-keep class com.ashnapatch.xposed.ModuleInfo { *; }
-dontwarn de.robv.android.xposed.**
