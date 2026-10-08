package com.loktv.hook;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/** Orchestrates every feature, isolates failures and reports a summary.
 *  v2.1.x: supports re-apply on a different ClassLoader (packed apps deliver
 *  a stub loader first, then the real one in attachBaseContext/onCreate).
 *  v2.2.0: same-loader re-apply allowed for late dex (HookRegistry dedups). */
public final class Engine {

    private static final Object LOCK = new Object();
    private static int sApplies = 0;
    /** v3.2.0: raised to allow chained late-dex retries (1.5s/4s/10s). */
    private static final int MAX_APPLIES = 6;

    public static void apply(final ClassLoader cl, final String pkg, final Context ctx) {
        synchronized (LOCK) {
            if (sApplies >= MAX_APPLIES) return;
            sApplies++;
        }
        try {
            Log.init(ctx, pkg);
            Log.banner();
            Log.i("apply() start | pkg=" + pkg + " | cl=" + (cl == null ? "null" : cl.getClass().getName()));
            Log.i("config: " + HookConfig.pathOf(ctx, pkg) + " | log: " + Log.path());

            final HookConfig cfg = HookConfig.load(ctx, pkg);
            final int[] counters = new int[]{0, 0};
            final List<String> failures = new ArrayList<String>();

            Features.Counter counter = new Features.Counter() {
                @Override
                public void ok(String what) {
                    counters[0]++;
                    if (cfg.debugVerbose) Log.i(what);
                }

                @Override
                public void fail(String what, Throwable t) {
                    counters[1]++;
                    failures.add(what);
                    Log.w("skip: " + what + (t == null ? "" : " :: " + Log.describe(t)));
                }
            };

            step("F21 legacy installer invoke", new Step() {
                public void run() { Features.legacyInstallers(cl, cfg, counter); }
            }, counters);
            step("F01-F05 user status", new Step() {
                public void run() { Features.userStatus(cl, cfg, counter); }
            }, counters);
            step("F06 tamper detection", new Step() {
                public void run() { Features.antiDetect(cl, cfg, counter); }
            }, counters);
            step("F07 anti-vpn", new Step() {
                public void run() { Features.antiVpn(cl, cfg, counter); }
            }, counters);
            step("F08 floating view", new Step() {
                public void run() { Features.floatingView(cl, cfg, counter); }
            }, counters);
            step("F09 rotation", new Step() {
                public void run() { Features.rotation(cl, cfg, counter); }
            }, counters);
            step("F10 generic dex scanner", new Step() {
                public void run() { Features.genericScanner(cl, cfg, counter); }
            }, counters);
            step("F15 ads & splash block", new Step() {
                public void run() { Features.adsBlock(cl, cfg, counter); }
            }, counters);
            step("F16 tracker block", new Step() {
                public void run() { Features.trackerBlock(cl, cfg, counter); }
            }, counters);
            step("F17 update dialog block", new Step() {
                public void run() { Features.updateDialogBlock(cl, cfg, counter); }
            }, counters);
            step("F18 hide VIP purchase UI", new Step() {
                public void run() { Features.hideVipUi(cl, cfg, counter); }
            }, counters);
            step("F19 VipItem unlock", new Step() {
                public void run() { Features.vipItemUnlock(cl, cfg, counter); }
            }, counters);
            step("F20 license/store-redirect bypass", new Step() {
                public void run() { Features.licenseBypass(cl, cfg, counter); }
            }, counters);
            step("F12 crash guard", new Step() {
                public void run() { counters[0] += Features.crashGuard(); }
            }, counters);

            Log.i("apply() done | pass=" + passOf() + " | hooks=" + counters[0] + " | skipped=" + counters[1]
                    + " | unique=" + HookRegistry.size()
                    + " | classes scanned=" + ClassScanner.names(cl).size());
            if (!failures.isEmpty()) Log.i("skipped list: " + failures);
            // v3.2.0: visible proof-of-life (no logcat needed to confirm activity).
            proofToast(ctx, cfg, counters[0]);
        } catch (Throwable t) {
            Log.e("apply() fatal (contained)", t);
        }
    }

    /**
     * v3.2.0: shows "LOKTV Hook Pro vX | hooks=N" once on the UI thread.
     * This is the field test: if the toast appears, the module IS running
     * inside the target and hooks=N were installed. Disable via toast=0.
     */
    private static void proofToast(final Context ctx, final HookConfig cfg, final int hooks) {
        try {
            if (ctx == null || (cfg != null && !cfg.toast)) return;
            final Context app;
            try {
                Context ac = ctx.getApplicationContext();
                app = ac == null ? ctx : ac;
            } catch (Throwable t) {
                return;
            }
            final String text = ModuleInfo.MODULE_NAME + " v" + ModuleInfo.VERSION
                    + " | hooks=" + hooks;
            try {
                android.os.Handler h = new android.os.Handler(android.os.Looper.getMainLooper());
                h.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            android.widget.Toast.makeText(app, text,
                                    android.widget.Toast.LENGTH_LONG).show();
                        } catch (Throwable ignored) {}
                    }
                });
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    private static int passOf() {
        synchronized (LOCK) {
            return sApplies;
        }
    }

    private interface Step {
        void run();
    }

    private static void step(String label, Step s, int[] counters) {
        int before = counters[0];
        try {
            s.run();
        } catch (Throwable t) {
            Log.w("step failed: " + label + " :: " + Log.describe(t));
        }
        // v3.1.0: per-feature delta is always logged (field diagnostics).
        Log.i(label + " => +" + (counters[0] - before) + " hooks");
    }

    private static void step(String label, Step s) {
        try {
            s.run();
        } catch (Throwable t) {
            Log.w("step failed: " + label + " :: " + Log.describe(t));
        }
    }

    private Engine() {}
}
