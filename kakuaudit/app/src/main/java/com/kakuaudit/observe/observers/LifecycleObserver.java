package com.kakuaudit.observe.observers;

import com.kakuaudit.observe.core.ReflectionDiscovery;
import com.kakuaudit.observe.core.SafeGuard;

/** Activity / dialog lifecycle + WebView navigation (metadata-only). */
public final class LifecycleObserver implements KakuObserver {
    @Override public String name() { return "LifecycleObserver"; }

    @Override public void install(ClassLoader appLoader, ObserverCtx ctx) {
        Class<?> activity = ReflectionDiscovery.loadBest(appLoader,
                BlueprintStore.candidates("activityCandidates",
                        "android.app.Activity",
                        "androidx.activity.ComponentActivity").toArray(new String[0]));
        if (activity == null) {
            ctx.prot.record("android.app.Activity", "UNAVAILABLE",
                    "framework-lookup-failed");
            return;
        }
        final Class<?> act = activity;
        // Lifecycle: onCreate/onResume/onPause... (any signature incl. Bundle).
        for (String m : BlueprintStore.candidates("activityMethods",
                "onCreate", "onResume", "onPause", "onDestroy")) {
            final String mn = m;
            SafeGuard.runSafe("lifecycle:" + act.getName() + "#" + mn, ctx.prot, () -> {
                for (java.lang.reflect.Method mm : act.getDeclaredMethods()) {
                    if (mm.getName().equals(mn)) {
                        HookKit.hookMethod(mm, act.getName(), mn,
                                ctx.seq, ctx.bus, null);
                        break;
                    }
                }
            });
        }
        // WebView: loadUrl / navigation observed as URL metadata (sanitized).
        Class<?> wv = ReflectionDiscovery.loadBest(appLoader,
                BlueprintStore.candidates("webViewCandidates",
                        "android.webkit.WebView",
                        "androidx.webkit.WebViewCompat").toArray(new String[0]));
        if (wv != null) {
            HookKit.hookDiscovered(wv,
                    HookKit.q(BlueprintStore.pattern("webViewMethodPattern",
                            "(?i)(loadUrl|loadData|onPage|shouldOverride)")),
                    appLoader, ctx.prot, ctx.seq, ctx.bus, null);
        } else {
            ctx.prot.record("android.webkit.WebView", "UNAVAILABLE", "not-present");
        }
    }
}
