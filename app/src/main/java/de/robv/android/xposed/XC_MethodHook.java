package de.robv.android.xposed;

import de.robv.android.xposed.XCallback;
import java.lang.reflect.Member;

/* loaded from: classes.dex */
public abstract class XC_MethodHook extends XCallback {

    public static class MethodHookParam extends XCallback.Param {
        public Member method;
    }

    public interface Unhook {
        void unhook();
    }

    protected void afterHookedMethod(MethodHookParam methodHookParam) throws Throwable {
    }

    protected void beforeHookedMethod(MethodHookParam methodHookParam) throws Throwable {
    }

    protected XC_MethodHook() {
    }

    protected XC_MethodHook(int i) {
        super(i);
    }
}
