package de.robv.android.xposed;

/* JADX INFO: Access modifiers changed from: package-private */
/* loaded from: classes.dex */
public abstract class XCallback {
    public static final int PRIORITY_DEFAULT = 50;
    final int priority;

    public static class Param {
        public Object[] args;
        public Object thisObject;

        public Object getResult() {
            return null;
        }

        public Throwable getThrowable() {
            return null;
        }

        public void setResult(Object obj) {
        }

        public void setThrowable(Throwable th) {
        }
    }

    protected XCallback() {
        this.priority = 50;
    }

    protected XCallback(int i) {
        this.priority = i;
    }
}
