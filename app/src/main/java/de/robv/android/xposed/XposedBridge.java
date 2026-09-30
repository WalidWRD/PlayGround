package de.robv.android.xposed;

import android.util.Log;
import de.robv.android.xposed.XC_MethodHook;
import java.lang.reflect.Method;

/* loaded from: classes.dex */
public final class XposedBridge {
    private XposedBridge() {
    }

    public static void log(String str) {
        try {
            Log.i("Xposed", str);
        } catch (Throwable unused) {
        }
    }

    public static XC_MethodHook.Unhook hookMethod(Method method, XC_MethodHook xC_MethodHook) {
        return new XC_MethodHook.Unhook() { // from class: de.robv.android.xposed.XposedBridge.1
            @Override // de.robv.android.xposed.XC_MethodHook.Unhook
            public void unhook() {
            }
        };
    }
}
