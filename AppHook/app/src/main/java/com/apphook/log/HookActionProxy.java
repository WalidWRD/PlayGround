package com.apphook.log;

import io.github.libxposed.api.XposedInterface;

/** دالة مشتركة: after-hook آمنة */
public interface HookActionProxy {
    Object run(XposedInterface.Chain chain, Object original) throws Throwable;
}
