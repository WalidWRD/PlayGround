package com.kakuaudit.observe.core;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Version-agnostic discovery via lpparam.classLoader + reflection.
 * No hard-coded target class/method names: callers pass behavioral
 * patterns (return type, arity, name regex, annotation) and we scan
 * the app's own ClassLoader. Framework classes are resolved reflectively
 * too so higher/lower Android versions both work.
 */
public final class ReflectionDiscovery {

    public static final class Query {
        public Pattern className;      // e.g. billing / license hint (DISCOVERY_HINT only)
        public Pattern methodName;     // behavioral regex, may be null (= any)
        public Class<?> returnType;    // may be null (= any)
        public int arity = -1;         // -1 = any
        public int maxScan = 4000;
    }

    public static final class Hit {
        public final Class<?> clazz;
        public final Method method;
        public Hit(Class<?> c, Method m) { this.clazz = c; this.method = m; }
    }

    private ReflectionDiscovery() {}

    /**
     * Scan already-loaded classes is impossible without VM introspection,
     * so we probe: (1) explicit candidates from dex enumeration handled by
     * caller, (2) framework classes via reflective lookup with fallbacks.
     * This helper applies the behavioral filter — the part that keeps us
     * version-agnostic (no fixed signatures).
     */
    public static List<Hit> filterMethods(Class<?> clazz, Query q) {
        if (clazz == null) return Collections.emptyList();
        Method[] methods;
        try {
            methods = clazz.getDeclaredMethods();
        } catch (Throwable t) {
            return Collections.emptyList(); // packed / inaccessible -> caller records PROTECTED
        }
        List<Hit> out = new ArrayList<>();
        for (Method m : methods) {
            try {
                if (q.methodName != null && !q.methodName.matcher(m.getName()).find()) continue;
                if (q.returnType != null && !q.returnType.isAssignableFrom(m.getReturnType())) continue;
                if (q.arity >= 0 && m.getParameterTypes().length != q.arity) continue;
                out.add(new Hit(clazz, m));
            } catch (Throwable ignore) { /* per-method isolation */ }
        }
        return out;
    }

    /**
     * Load a class through the TARGET app ClassLoader (never our own),
     * trying a list of candidate names ordered new->old so higher versions
     * win and lower versions fall back. Returns null instead of throwing.
     * Candidates are framework/structural, never app-protection bypass logic.
     */
    public static Class<?> loadBest(ClassLoader appLoader, String... candidates) {
        if (appLoader == null || candidates == null) return null;
        for (String name : candidates) {
            try {
                Class<?> c = Class.forName(name, false, appLoader);
                if (c != null) return c;
            } catch (Throwable ignore) { /* try next (older/newer) */ }
        }
        // Last resort: our own loader for pure-framework types.
        for (String name : candidates) {
            try {
                return Class.forName(name);
            } catch (Throwable ignore) { }
        }
        return null;
    }

    /** Walk ClassLoader chain for diagnostics (packed apps have extra delegates). */
    public static String describeChain(ClassLoader l) {
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        while (l != null && depth < 8) {
            if (sb.length() > 0) sb.append(" <- ");
            sb.append(l.getClass().getName());
            try {
                l = l.getParent();
            } catch (Throwable t) { break; }
            depth++;
        }
        return sb.toString();
    }
}
