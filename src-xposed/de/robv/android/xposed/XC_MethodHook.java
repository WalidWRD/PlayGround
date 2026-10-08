package de.robv.android.xposed;

import java.lang.reflect.Member;

/**
 * Compile-time stub of the Xposed API (api-82 compatible).
 * NOT packaged into the APK: the runtime implementation is injected by
 * LSPatch / NPatch / HKP / LSPosed. Used only as a library for d8.
 */
public abstract class XC_MethodHook extends XC_Callback {

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}

    public XC_MethodHook() { super(); }
    public XC_MethodHook(int priority) { super(priority); }

    public static class MethodHookParam extends XC_Callback.Param {
        public Member method;
        public Object thisObject;
        public Object[] args;
    }

    public static class Unhook {
        public Object getHookedMethod() { return null; }
        public void unhook() {}
    }
}
