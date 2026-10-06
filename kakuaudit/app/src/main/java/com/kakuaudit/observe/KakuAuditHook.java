package com.kakuaudit.observe;

import android.content.Context;

import com.kakuaudit.observe.core.EventBus;
import com.kakuaudit.observe.core.ProtectedComponentRegistry;
import com.kakuaudit.observe.core.ReflectionDiscovery;
import com.kakuaudit.observe.core.SafeGuard;
import com.kakuaudit.observe.observers.BlueprintStore;
import com.kakuaudit.observe.observers.ObserverCtx;
import com.kakuaudit.observe.observers.ObserverRegistry;
import com.kakuaudit.observe.report.ReportWriter;
import com.kakuaudit.observe.storage.SessionManager;
import com.kakuaudit.observe.storage.StorageRouter;
import com.kakuaudit.observe.watchers.EntitlementAnalysisWatcher;
import com.kakuaudit.observe.watchers.LicenseAnalysisWatcher;
import com.kakuaudit.observe.watchers.SubscriptionAnalysisWatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * KakuAudit v3.1 — OBSERVE_ONLY hook entry.
 *
 * <p>Multi-version: every target lookup goes through
 * {@code lpparam.classLoader} + ReflectionDiscovery with new-&gt;old
 * candidate ordering from the stored blueprint; nothing is hard-coded to
 * one app version or one Android API level.</p>
 *
 * <p>Crash-safe: per-package install set, every observer isolated via
 * SafeGuard, hook callbacks never throw, flush on a daemon thread.
 * A failing observer is kill-switched, never the app.</p>
 *
 * <p>Packed / protected apps: PROTECTED_OR_UNAVAILABLE is recorded and
 * observation continues; nothing is unpacked, decrypted, or bypassed.</p>
 *
 * <p>Rootless (LSPatch / NPatch / HKPatch-app): pure LSPosed Java API,
 * no su, no /data traversal, MediaStore/SAF/app-private storage only.</p>
 *
 * <p>OBSERVE_ONLY: afterHookedMethod for metadata logging only.
 * No param.args mutation, no setResult, no XC_MethodReplacement.</p>
 */
public final class KakuAuditHook {

    /** Per-package guard (multi-package in one process, e.g. WebView providers). */
    private static final Set<String> installedPkgs =
            Collections.synchronizedSet(new HashSet<String>());

    private KakuAuditHook() {}

    /**
     * Called from the Xposed entry (see xposed_init) via reflection so this
     * class has no hard compile dependency on the Xposed API.
     */
    public static void handleLoadPackage(Object lpparam) {
        if (lpparam == null) return;
        final String pkg;
        final ClassLoader appLoader;
        try {
            Class<?> lp = lpparam.getClass();
            pkg = (String) lp.getField("packageName").get(lpparam);
            appLoader = (ClassLoader) lp.getField("classLoader").get(lpparam);
        } catch (Throwable t) {
            return; // malformed param — never touch the app.
        }
        if (pkg == null || appLoader == null) return;
        // Never hook ourselves; never hook system server.
        if ("com.kakuaudit.observe".equals(pkg) || "android".equals(pkg)) return;
        // One install per package per process — atomic, no double-hook storms.
        synchronized (installedPkgs) {
            if (!installedPkgs.add(pkg)) return;
        }

        ModuleDescription.logToXposed("target=" + pkg);

        final ProtectedComponentRegistry prot = new ProtectedComponentRegistry();
        SafeGuard.runSafe("install:" + pkg, prot, () ->
                installForPackage(pkg, appLoader, prot));
    }

    private static void installForPackage(String pkg, ClassLoader appLoader,
                                          ProtectedComponentRegistry prot) {
        android.app.Application app = currentApplicationReflective();
        final Context ctx = app != null ? app.getApplicationContext() : null;

        // Stored blueprint first: no re-analysis on every launch.
        if (ctx != null) {
            SafeGuard.runSafe("blueprint:load", prot, () ->
                    BlueprintStore.load(moduleContext(ctx)));
        }

        SafeGuard.runSafe("packed:detect", prot, () -> {
            String chain = ReflectionDiscovery.describeChain(appLoader);
            if (chain.length() > 120 || chain.toLowerCase().contains("protect")) {
                prot.record(pkg, ProtectedComponentRegistry
                        .classifyLoaderChain(chain), chain);
            }
        });

        final List<String> lines =
                Collections.synchronizedList(new ArrayList<String>());
        final EventBus bus = new EventBus(10000, 0.25, line -> {
            // Sink must never throw back into the app thread.
            SafeGuard.runSafe("bus:sink", null, () -> lines.add(line));
        });
        final AtomicLong seq = new AtomicLong();

        final LicenseAnalysisWatcher lic =
                new LicenseAnalysisWatcher(bus, "pending", pkg);
        final EntitlementAnalysisWatcher ent =
                new EntitlementAnalysisWatcher(bus, "pending", pkg);
        final SubscriptionAnalysisWatcher sub =
                new SubscriptionAnalysisWatcher(bus, "pending", pkg);

        // Version-agnostic observers from the registry (each isolated).
        ObserverCtx octx = new ObserverCtx(bus, seq, prot, pkg, "pending",
                lic, ent, sub);
        ObserverRegistry.installAll(appLoader, octx);

        // Session + storage resolved lazily on flush to avoid early-context issues.
        if (ctx != null) {
            Thread t = SafeGuard.thread("KakuAudit-Flush", () ->
                    flushOnce(ctx, pkg, bus, prot, lines), prot);
            try {
                t.start();
            } catch (Throwable ignore) { /* thread start must never crash app */ }
        }
    }

    /** Our own module context (for assets) — never the target's. */
    private static Context moduleContext(Context targetCtx) {
        try {
            return targetCtx.createPackageContext("com.kakuaudit.observe",
                    Context.CONTEXT_IGNORE_SECURITY);
        } catch (Throwable t) {
            return targetCtx;
        }
    }

    private static android.app.Application currentApplicationReflective() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            java.lang.reflect.Method cur = at.getMethod("currentApplication");
            return (android.app.Application) cur.invoke(null);
        } catch (Throwable t) {
            return null;
        }
    }

    private static void flushOnce(final Context ctx, final String pkg,
                                  final EventBus bus,
                                  final ProtectedComponentRegistry prot,
                                  final List<String> lines) throws Exception {
        Thread.sleep(15000); // collect first window, then commit session files.
        final String version = appVersion(ctx, pkg);
        StorageRouter.Resolution res = StorageRouter.resolve(ctx, pkg, version,
                com.kakuaudit.observe.core.PathSafety.newSessionId());
        SessionManager sm = new SessionManager(ctx, pkg, version, res);
        if (!sm.acquire()) return;
        StringBuilder jsonl = new StringBuilder();
        synchronized (lines) {
            for (String l : lines) jsonl.append(l).append('\n');
        }
        List<String> errors = new ArrayList<>();
        for (String l : lines) {
            com.kakuaudit.observe.report.JsonValidator.Result r =
                    com.kakuaudit.observe.report.JsonValidator.validateLine(l);
            if (!r.ok) errors.addAll(r.errors);
        }
        boolean ok = false;
        try {
            new ReportWriter(sm).writeAll(jsonl.toString(), prot.snapshot(),
                    bus.dropped(), bus.accepted(), errors);
            ok = true;
        } finally {
            SafeGuard.runSafe("session:release", prot, sm::release);
            if (!ok) {
                SafeGuard.runSafe("session:mark-partial", prot, sm::release);
            }
        }
    }

    private static String appVersion(Context ctx, String pkg) {
        try {
            android.content.pm.PackageInfo pi =
                    ctx.getPackageManager().getPackageInfo(pkg, 0);
            return pi.versionName == null ? "0" : pi.versionName;
        } catch (Throwable t) {
            return "0";
        }
    }

    /** For host-side tests: path of the xposed_init entry. */
    public static String xposedEntryClass() {
        return "com.kakuaudit.observe.KakuAuditXposed";
    }
}
