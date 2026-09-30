package de.robv.android.xposed;

import java.lang.reflect.Member;

/** استبوب للترجمة فقط. */
public abstract class XC_MethodHook {
    public XC_MethodHook() { }
    public XC_MethodHook(int priority) { }
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable { }
    protected void afterHookedMethod(MethodHookParam param) throws Throwable { }

    public static class MethodHookParam {
        public Member method;
        public Object thisObject;
        public Object[] args;
        private Object result;
        public Object getResult() { return result; }
        public void setResult(Object result) { this.result = result; }
        public Throwable getThrowable() { return null; }
        public void setThrowable(Throwable t) { }
        public boolean hasThrowable() { return false; }
    }

    public class Unhook {
        private final Member hookMethod;
        public Unhook(Member hookMethod) { this.hookMethod = hookMethod; }
        public Member getHookedMethod() { return hookMethod; }
        public void unhook() { }
    }
}
