package com.apphook.log;

/** حارس: لا نربط Hook على فئات Proxy الديناميكية أبداً. */
public final class ProxyMarker {
    private ProxyMarker() {}

    public static boolean isProxy(Class<?> c) {
        if (c == null) return true;
        try {
            if (java.lang.reflect.Proxy.isProxyClass(c)) return true;
        } catch (Throwable ignored) {}
        try {
            String n = c.getName();
            if (n != null && (n.contains("$Proxy") || n.contains("Proxy"))) return true;
        } catch (Throwable ignored) {}
        return false;
    }
}
