package com.loktv.hook;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Enumerates every class name inside the target APK by walking the
 * BaseDexClassLoader path list. Handles all ART layouts:
 *   - Element[] with a "dexFile" field        (API 21 .. ~28)
 *   - DexFile[] directly inside dexElements   (API 29+ / Android 10..15)
 * This is what makes the module survive class renaming, obfuscation and
 * different app versions instead of relying on hard-coded names.
 */
public final class ClassScanner {

    public static final int DEFAULT_LIMIT = 40000;

    private static final java.util.Map<Integer, List<String>> sNamesCache =
            new java.util.HashMap<Integer, List<String>>();

    /** v2.2.0: cache of loaded target classes per ClassLoader, so the
     *  10 feature scans in one apply() share a single Class.forName pass. */
    private static final java.util.Map<Integer, List<Class<?>>> sClassesCache =
            new java.util.HashMap<Integer, List<Class<?>>>();
    private static final java.util.Map<Integer, String> sClassesKey =
            new java.util.HashMap<Integer, String>();

    /** All class names shipped in the APK dex files. Never throws. */
    public static List<String> names(ClassLoader cl) {
        if (cl != null) {
            synchronized (sNamesCache) {
                List<String> cached = sNamesCache.get(Integer.valueOf(System.identityHashCode(cl)));
                if (cached != null) return cached;
            }
        }
        List<String> out = scanNames(cl);
        if (cl != null) {
            synchronized (sNamesCache) {
                if (sNamesCache.size() < 8) {
                    sNamesCache.put(Integer.valueOf(System.identityHashCode(cl)), out);
                }
            }
        }
        return out;
    }

    private static List<String> scanNames(ClassLoader cl) {
        List<String> out = new ArrayList<String>();
        Set<String> seen = new HashSet<String>();
        try {
            Object pathList = fieldOf(cl, "pathList");
            if (pathList == null) return out;
            Object elementsObj = fieldOf(pathList, "dexElements");
            if (!(elementsObj instanceof Object[])) return out;
            Object[] elements = (Object[]) elementsObj;
            for (Object el : elements) {
                Object dexFile = el;
                if (!hasEntries(dexFile)) dexFile = fieldOf(el, "dexFile");
                if (dexFile == null || !hasEntries(dexFile)) continue;
                try {
                    Method entries = dexFile.getClass().getMethod("entries");
                    entries.setAccessible(true);
                    Object res = entries.invoke(dexFile);
                    if (res instanceof Enumeration) {
                        Enumeration<?> en = (Enumeration<?>) res;
                        while (en.hasMoreElements()) {
                            Object n = en.nextElement();
                            if (n instanceof String && seen.add((String) n)) out.add((String) n);
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return out;
    }

    /** Loads classes whose fully-qualified name starts with one of the prefixes.
     *  Falls back to an unfiltered scan when the prefix scan is empty
     *  (obfuscated builds), capped by the same limit.
     *  v2.2.0: results are cached per (loader, prefixes, limit). */
    public static List<Class<?>> classes(ClassLoader cl, String[] prefixes, int limit) {
        if (cl != null) {
            String ck = cacheKey(prefixes, limit);
            synchronized (sClassesCache) {
                Integer id = Integer.valueOf(System.identityHashCode(cl));
                List<Class<?>> cached = sClassesCache.get(id);
                String oldKey = sClassesKey.get(id);
                if (cached != null && ck.equals(oldKey)) return cached;
            }
        }
        List<Class<?>> out = loadClasses(cl, prefixes, limit);
        if (cl != null) {
            synchronized (sClassesCache) {
                if (sClassesCache.size() < 8) {
                    Integer id = Integer.valueOf(System.identityHashCode(cl));
                    sClassesCache.put(id, out);
                    sClassesKey.put(id, cacheKey(prefixes, limit));
                }
            }
        }
        return out;
    }

    private static String cacheKey(String[] prefixes, int limit) {
        StringBuilder sb = new StringBuilder();
        sb.append(limit).append('|');
        if (prefixes != null) {
            for (String p : prefixes) sb.append(p == null ? "?" : p).append(',');
        }
        return sb.toString();
    }

    private static List<Class<?>> loadClasses(ClassLoader cl, String[] prefixes, int limit) {
        List<Class<?>> out = new ArrayList<Class<?>>();
        List<String> all = names(cl);
        for (String n : all) {
            if (out.size() >= limit) break;
            if (!matchPrefix(n, prefixes)) continue;
            try {
                out.add(Class.forName(n, false, cl));
            } catch (Throwable ignored) {}
        }
        if (out.isEmpty() && all.size() > 0) {
            for (String n : all) {
                if (out.size() >= limit) break;
                if (n.startsWith("android.") || n.startsWith("java.")
                        || n.startsWith("kotlin.") || n.startsWith("androidx.")) continue;
                try {
                    out.add(Class.forName(n, false, cl));
                } catch (Throwable ignored) {}
            }
        }
        return out;
    }

    /** Finds a class by its simple name regardless of its package. */
    public static Class<?> bySimpleName(ClassLoader cl, String simpleName, String[] prefixes) {
        for (String n : names(cl)) {
            if (!n.endsWith("." + simpleName)) continue;
            if (!matchPrefix(n, prefixes)) continue;
            try { return Class.forName(n, false, cl); } catch (Throwable ignored) {}
        }
        return null;
    }

    public static boolean matchPrefix(String name, String[] prefixes) {
        if (name == null) return false;
        if (prefixes == null || prefixes.length == 0) return true;
        for (String p : prefixes) if (p != null && name.startsWith(p)) return true;
        return false;
    }

    static Object fieldOf(Object target, String name) {
        if (target == null) return null;
        Class<?> c = target.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (Throwable t) {
                c = c.getSuperclass();
            }
        }
        return null;
    }

    private static boolean hasEntries(Object o) {
        if (o == null) return false;
        try {
            o.getClass().getMethod("entries");
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private ClassScanner() {}
}
