package de.robv.android.xposed;

public abstract class XC_MethodReplacement extends XC_MethodHook {

    public static final XC_MethodReplacement DO_NOTHING = new XC_MethodReplacement() {
        @Override
        protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
            return null;
        }
    };

    public XC_MethodReplacement() { super(); }
    public XC_MethodReplacement(int priority) { super(priority); }

    protected abstract Object replaceHookedMethod(MethodHookParam param) throws Throwable;

    @Override
    protected final void beforeHookedMethod(MethodHookParam param) throws Throwable {
        param.setResult(replaceHookedMethod(param));
    }
}
