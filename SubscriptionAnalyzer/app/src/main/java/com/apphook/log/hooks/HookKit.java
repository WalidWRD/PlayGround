package com.apphook.log.hooks;

import com.apphook.log.SafeHook;

import io.github.libxposed.api.XposedInterface;

import java.lang.reflect.Method;

/**
 * مساعد الربط المشترك (API 102): يربط كل declared عبر الهرم الوراثي.
 * PROTECTIVE دائماً + تجاهل آمن. (لا XposedHelpers في API 102.)
 */
public final class HookKit {
    private HookKit() {}

    public interface ChainRun {
        Object run(XposedInterface.Chain chain) throws Throwable;
    }

    public static void hookAll(XposedInterface fw, Class<?> base, String name,
                               int minArgs, int maxArgs, String tag, ChainRun run) {
        Class<?> c = base;
        while (c != null && c != Object.class) {
            Method[] ms;
            try {
                ms = c.getDeclaredMethods();
            } catch (Throwable ignored) {
                break;
            }
            for (Method m : ms) {
                if (!m.getName().equals(name)) continue;
                int n = m.getParameterTypes().length;
                if (n < minArgs || (maxArgs >= 0 && n > maxArgs)) continue;
                try {
                    m.setAccessible(true);
                    fw.hook(m)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                try {
                                    return run.run(chain);
                                } catch (Throwable t) {
                                    SafeHook.logError(tag, t);
                                    try {
                                        return chain.proceed();
                                    } catch (Throwable t2) {
                                        return null;
                                    }
                                }
                            });
                    SafeHook.log("Hook OK: " + tag + " -> " + m);
                } catch (Throwable t) {
                    SafeHook.logError(tag, t);
                }
            }
            try {
                c = c.getSuperclass();
            } catch (Throwable ignored) {
                break;
            }
        }
    }

    public static void hookOne(XposedInterface fw, Method m, String tag, ChainRun run) {
        if (m == null) {
            SafeHook.logSafe(tag, "method-not-found");
            return;
        }
        try {
            m.setAccessible(true);
            fw.hook(m)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        try {
                            return run.run(chain);
                        } catch (Throwable t) {
                            SafeHook.logError(tag, t);
                            try {
                                return chain.proceed();
                            } catch (Throwable t2) {
                                return null;
                            }
                        }
                    });
            SafeHook.log("Hook OK: " + tag + " -> " + m);
        } catch (Throwable t) {
            SafeHook.logError(tag, t);
        }
    }
}
