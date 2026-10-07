package com.loktv.hook;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/** Orchestrates every feature, isolates failures and reports a summary.
 *  v2.1.0: supports re-apply on a different ClassLoader (packed apps deliver
 *  a stub loader first, then the real one in attachBaseContext/onCreate). */
public final class Engine {

    private static final Object LOCK = new Object();
    private static final java.util.Set<Integer> sAppliedLoaders =
            new java.util.HashSet<Integer>();
    private static final int MAX_APPLIES = 3;

    public static void apply(final ClassLoader cl, final String pkg, final Context ctx) {
        final int id = System.identityHashCode(cl);
        synchronized (LOCK) {
            if (cl != null && sAppliedLoaders.contains(Integer.valueOf(id))) return;
            if (sAppliedLoaders.size() >= MAX_APPLIES) return;
            sAppliedLoaders.add(Integer.valueOf(id));
        }
        try {
            Log.init(ctx, pkg);
            Log.banner();
            Log.i("apply() start | pkg=" + pkg + " | cl=" + (cl == null ? "null" : cl.getClass().getName()));

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

            step("F01-F05 user status", new Step() {
                public void run() { Features.userStatus(cl, cfg, counter); }
            });
            step("F06 tamper detection", new Step() {
                public void run() { Features.antiDetect(cl, cfg, counter); }
            });
            step("F07 anti-vpn", new Step() {
                public void run() { Features.antiVpn(cl, cfg, counter); }
            });
            step("F08 floating view", new Step() {
                public void run() { Features.floatingView(cl, cfg, counter); }
            });
            step("F09 rotation", new Step() {
                public void run() { Features.rotation(cl, cfg, counter); }
            });
            step("F10 generic dex scanner", new Step() {
                public void run() { Features.genericScanner(cl, cfg, counter); }
            });
            step("F15 ads & splash block", new Step() {
                public void run() { Features.adsBlock(cl, cfg, counter); }
            });
            step("F16 tracker block", new Step() {
                public void run() { Features.trackerBlock(cl, cfg, counter); }
            });
            step("F17 update dialog block", new Step() {
                public void run() { Features.updateDialogBlock(cl, cfg, counter); }
            });
            step("F12 crash guard", new Step() {
                public void run() { counters[0] += Features.crashGuard(); }
            });

            Log.i("apply() done | hooks=" + counters[0] + " | skipped=" + counters[1]
                    + " | classes scanned=" + ClassScanner.names(cl).size());
            if (!failures.isEmpty()) Log.i("skipped list: " + failures);
        } catch (Throwable t) {
            Log.e("apply() fatal (contained)", t);
        }
    }

    private interface Step {
        void run();
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
