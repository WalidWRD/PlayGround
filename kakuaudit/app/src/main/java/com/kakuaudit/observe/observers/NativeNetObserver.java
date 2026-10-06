package com.kakuaudit.observe.observers;

import com.kakuaudit.observe.core.EventBus;
import com.kakuaudit.observe.core.KakuClock;
import com.kakuaudit.observe.core.Redactor;
import com.kakuaudit.observe.core.ReflectionDiscovery;
import com.kakuaudit.observe.core.SafeGuard;
import com.kakuaudit.observe.report.Jsons;

import java.util.UUID;

/**
 * Native lib loads + network metadata (method/status/timing only).
 * Never exports headers, bodies, tokens. Transport never weakened.
 */
public final class NativeNetObserver implements KakuObserver {
    @Override public String name() { return "NativeNetObserver"; }

    @Override public void install(ClassLoader appLoader, ObserverCtx ctx) {
        // System.loadLibrary / Runtime.loadLibrary — framework reflective lookup.
        SafeGuard.runSafe("native:Runtime", ctx.prot, () -> {
            Class<?> runtime = ReflectionDiscovery.loadBest(appLoader,
                    BlueprintStore.pattern("runtimeClass", "java.lang.Runtime"));
            if (runtime != null) {
                for (java.lang.reflect.Method m : runtime.getDeclaredMethods()) {
                    if (m.getName().startsWith("load")) {
                        final String mn = m.getName();
                        HookKit.hookMethod(m, "java.lang.Runtime", mn,
                                ctx.seq, ctx.bus,
                                (c, meth, ret, s) -> emitNative(ctx.bus, c, meth));
                    }
                }
            }
        });
        // HttpURLConnection (all versions) + OkHttp (when present) — metadata only.
        Class<?> huc = ReflectionDiscovery.loadBest(appLoader,
                BlueprintStore.candidates("httpCandidates",
                        "java.net.HttpURLConnection",
                        "com.android.okhttp.internal.huc.HttpURLConnectionImpl")
                        .toArray(new String[0]));
        if (huc != null) {
            HookKit.hookDiscovered(huc,
                    HookKit.q(BlueprintStore.pattern("httpMethodPattern",
                            "(?i)(connect|getResponseCode|getRequestMethod)")),
                    appLoader, ctx.prot, ctx.seq, ctx.bus,
                    (c, m, ret, s) -> emitNet(ctx.bus, c, m));
        }
        Class<?> okhttp = ReflectionDiscovery.loadBest(appLoader,
                BlueprintStore.candidates("okhttpCandidates",
                        "okhttp3.OkHttpClient",
                        "okhttp3.Request",
                        "com.squareup.okhttp.OkHttpClient").toArray(new String[0]));
        if (okhttp != null) {
            HookKit.hookDiscovered(okhttp,
                    HookKit.q(BlueprintStore.pattern("okhttpMethodPattern",
                            "(?i)(newCall|execute|enqueue)")),
                    appLoader, ctx.prot, ctx.seq, ctx.bus,
                    (c, m, ret, s) -> emitNet(ctx.bus, c, m));
        } else {
            ctx.prot.record("okhttp3.OkHttpClient", "DYNAMICALLY_LOADED",
                    "not-present-in-this-version");
        }
    }

    private static void emitNative(EventBus bus, String clazz, String method) {
        if (bus == null) return;
        String sb = "{" + Jsons.kv("schemaVersion", Jsons.q("3.0")) + ","
                + Jsons.kv("eventId", Jsons.q(UUID.randomUUID().toString())) + ","
                + Jsons.kv("type", Jsons.q("NATIVE_LIBRARY_EVENT")) + ","
                + Jsons.kv("class", Jsons.q(clazz)) + ","
                + Jsons.kv("method", Jsons.q(method)) + ","
                + Jsons.kv("observedAt", Jsons.q(KakuClock.utcNowIso())) + "}";
        bus.publish(sb, System.nanoTime());
    }

    private static void emitNet(EventBus bus, String clazz, String method) {
        if (bus == null) return;
        String sb = "{" + Jsons.kv("schemaVersion", Jsons.q("3.0")) + ","
                + Jsons.kv("eventId", Jsons.q(UUID.randomUUID().toString())) + ","
                + Jsons.kv("type", Jsons.q("NETWORK_METADATA")) + ","
                + Jsons.kv("class", Jsons.q(clazz)) + ","
                + Jsons.kv("method", Jsons.q(method)) + ","
                + Jsons.kv("note", Jsons.q("metadata-only; URL sanitized via "
                + Redactor.class.getSimpleName())) + ","
                + Jsons.kv("observedAt", Jsons.q(KakuClock.utcNowIso())) + "}";
        bus.publish(sb, System.nanoTime());
    }
}
