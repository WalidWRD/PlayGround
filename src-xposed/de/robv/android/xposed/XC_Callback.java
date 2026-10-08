package de.robv.android.xposed;

/**
 * Compile-time stub matching the real Xposed API (api-82) hierarchy:
 * XC_MethodHook extends XC_Callback. NOT packaged into the APK.
 */
public abstract class XC_Callback {

    public static final int PRIORITY_HIGHEST = 10000;
    public static final int PRIORITY_HIGH = 1000;
    public static final int PRIORITY_DEFAULT = 50;
    public static final int PRIORITY_LOW = 10;
    public static final int PRIORITY_LOWEST = -10000;

    public int priority;

    public XC_Callback() { priority = PRIORITY_DEFAULT; }
    public XC_Callback(int priority) { this.priority = priority; }

    public static abstract class Param {
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
}
