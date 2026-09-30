package com.genspark.updatekiller.hooks;

import com.genspark.updatekiller.UxLog;

/* loaded from: classes.dex */
public abstract class TemplateHook {
    protected abstract boolean enabled();

    protected abstract String name();

    public static int install(ClassLoader classLoader) {
        UxLog.i("TemplateHook: loaded (no-op base for new hooks)");
        return 0;
    }
}
