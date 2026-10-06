package com.kakuaudit.observe;

import android.content.Context;

import com.kakuaudit.observe.core.EventBus;
import com.kakuaudit.observe.core.KakuClock;
import com.kakuaudit.observe.core.ProtectedComponentRegistry;
import com.kakuaudit.observe.core.ReflectionDiscovery;
import com.kakuaudit.observe.core.SafeGuard;
import com.kakuaudit.observe.observers.BlueprintStore;
import com.kakuaudit.observe.observers.ObserverCtx;
import com.kakuaudit.observe.observers.ObserverRegistry;
import com.kakuaudit.observe.report.JsonValidator;
import com.kakuaudit.observe.report.Jsons;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * KakuAudit v3.1.2 — OBSERVE_ONLY hook entry.
 *
 * <p>Multi-version: every target lookup goes through
 * {@code lpparam.classLoader} + ReflectionDiscovery with new-&gt;old
 * candidate ordering from the stored blueprint; nothing is hard-coded to
 * one app version or one Android API level.</p>
 *
 * <p>Crash-safe: per-package install set, every observer isolated via
 * SafeGuard, hook callbacks never throw, flush on a background thread.
 * A failing observer is kill-switched, never the app.</p>
 *
 * <p>v3.1.2 no-output fix: handleLoadPackage usually fires BEFORE
 * Application.onCreate, so a null Context no longer cancels the flush —
 * a waiter thread polls for the app context (up to ~2 min), an
 * INSTALL_MARKER event guarantees a non-empty session, and two flush
 * windows (20s + 90s) commit files even for short sessions. Every stage
 * is logged to the LSPosed log so missing files are diagnosable.</p>
 *
 * <p>Packed / protected apps: PROTECTED_OR_UNAVAILABLE is recorded and
 * observation continues; nothing is unpacked, decrypted, or bypassed.</p>
 *
 * <p>Rootless (LSPatch / NPatch / HKPatch-app): pure LSPosed Java API,
 * no su, no /data traversal. Session files go to public Download when
 * writable, else app-private staging + MediaStore export.</p>
 *
 * <p>OBSERVE_ONLY: afterHookedMethod for metadata logging only.
 * No param.args mutation, no setResult, no XC_MethodReplacement.</p>
 */
public final class KakuAuditHook {

    /** Per-package guard (multi-package in one process, e.g. WebView providers). */
    private static final Set<String> installedPkgs =
            Collections.synchronizedSet(new HashSet<String>());

    /** First flush delay (collection window) and second flush delay. */
    private static final long FLUSH_1_MS = 20000;
    private static final long FLUSH_2_MS = 90000;
    /** Max wait for the app Context before giving up (2s polls). */
    private static final int CTX_POLLS = 60;

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

        ModuleDescription.logToXposed("target=" + pkg + " | install-start");

        final ProtectedComponentRegistry prot = new ProtectedComponentRegistry();
        SafeGuard.runSafe("install:" + pkg, prot, () ->
                installForPackage(pkg, appLoader, prot));
    }

    private static void installForPackage(String pkg, ClassLoader appLoader,
                                          ProtectedComponentRegistry prot) {
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

        // INSTALL_MARKER first: session output is never empty, even with zero hooks.
        SafeGuard.runSafe("install:marker", prot, () ->
                lines.add(markerJson(pkg, appLoader)));

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
        ModuleDescription.logToXposed("target=" + pkg + " | observers-installed");

        // Flush waiter: polls for the app Context (handleLoadPackage fires before
        // Application.onCreate), then commits two session windows. Always started.
        Thread t = SafeGuard.thread("KakuAudit-Flush", () ->
                waitAndFlush(pkg, bus, prot, lines), prot);
        try {
            t.start();
        } catch (Throwable ignore) { /* thread start must never crash app */ }
    }

    private static String markerJson(String pkg, ClassLoader appLoader) {
        String chain;
        try {
            chain = ReflectionDiscovery.describeChain(appLoader);
        } catch (Throwable t) {
            chain = "UNAVAILABLE";
        }
        if (chain.length() > 512) chain = chain.substring(0, 512);
        return "{" + Jsons.kv("schemaVersion", Jsons.q("3.0")) + ","
                + Jsons.kv("eventId", Jsons.q(UUID.randomUUID().toString())) + ","
                + Jsons.kv("eventType", Jsons.q("INSTALL_MARKER")) + ","
                + Jsons.kv("process", Jsons.q(pkg)) + ","
                + Jsons.kv("module", Jsons.q("KakuAudit-Observe-"
                        + ModuleDescription.VERSION)) + ","
                + Jsons.kv("classLoaderChain", Jsons.q(chain)) + ","
                + Jsons.kv("observedAt", Jsons.q(KakuClock.utcNowIso())) + ","
                + Jsons.kv("monotonicTimeNanos",
                        String.valueOf(KakuClock.monotonicNanos())) + "}";
    }

    /** Poll for Context, then flush window 1 (20s) and window 2 (90s). */
    private static void waitAndFlush(String pkg, EventBus bus,
                                     ProtectedComponentRegistry prot,
                                     List<String> lines) throws Exception {
        Context ctx = null;
        for (int i = 0; i < CTX_POLLS; i++) {
            ctx = appContext();
            if (ctx != null) break;
            Thread.sleep(2000);
        }
        if (ctx == null) {
            prot.record("flush", "FAILED", "no-app-context-after-120s");
            ModuleDescription.logToXposed("target=" + pkg
                    + " | NO-CTX no session written (app context never appeared)");
            return;
        }
        final Context appCtx = ctx.getApplicationContext() != null
                ? ctx.getApplicationContext() : ctx;

        SafeGuard.runSafe("blueprint:load", prot, () ->
                BlueprintStore.load(moduleContext(appCtx)));
        ModuleDescription.logToXposed("target=" + pkg + " | ctx-ok");

        Thread.sleep(FLUSH_1_MS);
        flushWindow(appCtx, pkg, bus, prot, lines, "w1");
        Thread.sleep(FLUSH_2_MS - FLUSH_1_MS);
        flushWindow(appCtx, pkg, bus, prot, lines, "w2");
    }

    private static void flushWindow(Context ctx, String pkg, EventBus bus,
                                    ProtectedComponentRegistry prot,
                                    List<String> lines, String window) {
        final String version = appVersion(ctx, pkg);
        // Snapshot + clear so w2 holds only new events.
        final List<String> snap;
        synchronized (lines) {
            snap = new ArrayList<>(lines);
            lines.clear();
        }
        ModuleDescription.logToXposed("target=" + pkg + " | flush-" + window
                + " start events=" + snap.size());
        StorageRouter.Resolution res;
        try {
            res = StorageRouter.resolve(ctx, pkg, version,
                    com.kakuaudit.observe.core.PathSafety.newSessionId());
        } catch (Throwable t) {
            prot.record("flush-" + window, "FAILED",
                    "resolve:" + t.getClass().getSimpleName());
            ModuleDescription.logToXposed("target=" + pkg + " | flush-" + window
                    + " RESOLVE-FAIL " + t.getClass().getSimpleName());
            return;
        }
        if (res == null || !res.writable || res.sessionDir == null) {
            prot.record("flush-" + window, "FAILED",
                    res == null ? "null-resolution" : res.diagnostic);
            ModuleDescription.logToXposed("target=" + pkg + " | flush-" + window
                    + " UNWRITABLE " + (res == null ? "null" : res.diagnostic));
            return;
        }
        SessionManager sm = new SessionManager(ctx, pkg, version, res);
        if (!sm.acquire()) {
            ModuleDescription.logToXposed("target=" + pkg + " | flush-" + window
                    + " LOCK-FAIL");
            return;
        }
        try {
            StringBuilder jsonl = new StringBuilder();
            for (String l : snap) jsonl.append(l).append('\n');
            List<String> errors = new ArrayList<>();
            for (String l : snap) {
                JsonValidator.Result r = JsonValidator.validateLine(l);
                if (!r.ok) errors.addAll(r.errors);
            }
            new ReportWriter(sm).writeAll(jsonl.toString(), prot.snapshot(),
                    bus.dropped(), bus.accepted(), errors);
            int exported = 0;
            try {
                exported = StorageRouter.exportToDownloads(ctx, res);
            } catch (Throwable ignore) { /* export is best-effort */ }
            ModuleDescription.logToXposed("target=" + pkg + " | flush-" + window
                    + " DONE dir=" + res.actualRoot
                    + " mechanism=" + res.mechanism
                    + " exported=" + exported
                    + " events=" + snap.size());
        } catch (Throwable t) {
            prot.record("flush-" + window, "FAILED", t.getClass().getSimpleName());
            ModuleDescription.logToXposed("target=" + pkg + " | flush-" + window
                    + " WRITE-FAIL " + t.getClass().getSimpleName());
        } finally {
            SafeGuard.runSafe("session:release", prot, sm::release);
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

    private static Context appContext() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            java.lang.reflect.Method cur = at.getMethod("currentApplication");
            android.app.Application app =
                    (android.app.Application) cur.invoke(null);
            if (app == null) return null;
            Context c = app.getApplicationContext();
            return c != null ? c : app;
        } catch (Throwable t) {
            return null;
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
