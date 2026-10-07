package de.robv.android.xposed;

import java.lang.reflect.Member;

/**
 * Compile-time stub of the Xposed API (api-82 compatible).
 * NOT packaged into the APK: the runtime implementation is injected by
 * LSPatch / NPatch / HKP / LSPosed. Used only as a library for d8.
 */
public abstract class XC_MethodHook {

    public static final int PRIORITY_HIGHEST = 10000;
    public static final int PRIORITY_HIGH = 1000;
    public static final int PRIORITY_DEFAULT = 50;
    public static final int PRIORITY_LOW = 10;
    public static final int PRIORITY_LOWEST = -10000;

    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}

    public XC_MethodHook() {}
    public XC_MethodHook(int priority) {}

    public static class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;
        private Object result;
        private Throwable throwable;
        private boolean returnEarly;

        public Object getResult() { return result; }
        public void setResult(Object r) { result = r; returnEarly = true; }
        public Throwable getThrowable() { return throwable; }
        public void setThrowable(Throwable t) { throwable = t; returnEarly = true; }
        public boolean hasThrowable() { return throwable != null; }
        public Object getResultOrThrowable() throws Throwable {
            if (throwable != null) throw throwable;
            return result;
        }
    }

    public class Unhook {
        public Object getHookedMethod() { return null; }
        public void unhook() {}
    }
}
