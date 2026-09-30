package com.genspark.updatekiller;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * جديد 2.0.0 — التقرير الذاتي: يسجّل لكل مكوّن عدد الخطّافات التي نجحت،
 * ويكتب ملف JSON للتحقق على الجهاز (بدل إعادة التحليل اليدوي كل مرة).
 *
 * ملفات التقرير (تُجرَّب بالترتيب):
 *   1) /data/local/tmp/genspark_updatekiller_report.json
 *   2) /sdcard/Android/data/&lt;pkg&gt;/files/genspark_updatekiller_report.json
 *   3) /data/data/&lt;pkg&gt;/files/genspark_updatekiller_report.json
 *   4) /sdcard/Download/genspark_updatekiller_report.json
 */
public final class SelfTest {

    private static final String FILE_NAME = "genspark_updatekiller_report.json";
    private static final ConcurrentHashMap<String, Integer> COMPONENTS = new ConcurrentHashMap<String, Integer>();
    private static volatile String verdict = "PENDING";
    private static volatile String note = "";

    private SelfTest() { }

    public static void record(String component, int hooks) {
        if (component == null) return;
        try { COMPONENTS.put(component, Integer.valueOf(hooks)); } catch (Throwable ignored) { }
    }

    public static void addHooks(String component, int add) {
        if (component == null || add <= 0) return;
        try {
            Integer cur = COMPONENTS.get(component);
            COMPONENTS.put(component, Integer.valueOf((cur == null ? 0 : cur.intValue()) + add));
        } catch (Throwable ignored) { }
    }

    public static void finish(String v, String why) {
        verdict = (v == null) ? "UNKNOWN" : v;
        note = (why == null) ? "" : why;
    }

    public static int totalHooks() {
        int t = 0;
        for (Integer v : COMPONENTS.values()) if (v != null && v.intValue() > 0) t += v.intValue();
        return t;
    }

    public static String toJson(ClassLoader cl) {
        JSONObject root = new JSONObject();
        try {
            root.put("module", BuildInfo.MODULE_NAME);
            root.put("version", BuildInfo.VERSION_NAME);
            root.put("build", BuildInfo.VERSION_CODE);
            root.put("engine", "reflection+signature+dex-index (no hard-coded names)");

            JSONObject env = new JSONObject();
            env.put("android_api", EnvInfo.apiLevel());
            env.put("abi", EnvInfo.abis().length > 0 ? EnvInfo.abis()[0] : "unknown");
            env.put("patch_framework", EnvInfo.patchFramework(cl));
            env.put("source_dir", EnvInfo.sourceDir());
            env.put("native_lib_dir", EnvInfo.nativeLibraryDir());
            root.put("env", env);

            JSONObject target = new JSONObject();
            target.put("package", EnvInfo.packageName());
            target.put("version_name", EnvInfo.realVersionName());
            target.put("version_code", EnvInfo.realVersionCode());
            target.put("spoofed_version_code", Config.get().spoofVersionCode);
            target.put("spoofed_version_name", Config.get().spoofVersionName);
            root.put("target", target);

            JSONObject dis = new JSONObject();
            dis.put("dex_scanned", Discovery.isDexScanned());
            dis.put("class_names_indexed", Discovery.names());
            dis.put("source", Discovery.scanSource());
            dis.put("classes_loaded_at_runtime", Discovery.loadedCount());
            dis.put("lazy_install_passes", DeferredInstaller.passesRun());
            dis.put("pending_tasks", DeferredInstaller.pendingList());
            root.put("discovery", dis);

            JSONObject hooks = new JSONObject();
            for (String k : sortedKeys()) {
                Integer v = COMPONENTS.get(k);
                hooks.put(k, (v == null) ? 0 : v.intValue());
            }
            root.put("hooks", hooks);

            JSONObject guard = new JSONObject();
            guard.put("failures", Guard.failures());
            guard.put("kill_switch_tripped", Guard.isTripped());
            guard.put("threshold", Config.get().killSwitchThreshold);
            root.put("guard", guard);

            try {
                JSONObject sub = new JSONObject();
                sub.put("enabled", Config.get().subscriptionBridge);
                sub.put("state", SubscriptionBridge.getState().name());
                sub.put("active", SubscriptionBridge.isActive());
                sub.put("plan", SubscriptionBridge.getPlan());
                sub.put("active_until", SubscriptionBridge.getActiveUntil());
                sub.put("user_id", SubscriptionBridge.getUserId());
                sub.put("last_error", SubscriptionBridge.getLastError());
                root.put("subscription", sub);
            } catch (Throwable t) {
                Guard.record("SelfTest.subscription", t);
            }

            root.put("total_hooks", totalHooks());
            root.put("verdict", verdict);
            root.put("note", note);
            root.put("written_at", System.currentTimeMillis());
        } catch (Throwable t) {
            Guard.record("SelfTest.toJson", t);
        }
        return root.toString();
    }

    private static List<String> sortedKeys() {
        List<String> out = new ArrayList<String>(COMPONENTS.keySet());
        try { java.util.Collections.sort(out); } catch (Throwable ignored) { }
        return out;
    }

    /** يكتب التقرير إلى أول مسار قابل للكتابة — لا يرمي أبدًا. */
    public static void write(ClassLoader cl, String pkg) {
        Guard.run("SelfTest.write", new Guard.Action() {
            @Override public void run() {
                String json = toJson(cl);
                String[] paths = paths(pkg);
                for (String p : paths) {
                    try {
                        File f = new File(p);
                        File dir = f.getParentFile();
                        if (dir != null && !dir.exists()) dir.mkdirs();
                        FileOutputStream out = new FileOutputStream(f, false);
                        out.write(json.getBytes("UTF-8"));
                        out.flush();
                        out.close();
                        UxLog.i("SelfTest: report written → " + p);
                    } catch (Throwable t) {
                        Guard.record("SelfTest.write(" + p + ")", t);
                    }
                }
            }
        });
    }

    private static String[] paths(String pkg) {
        String p = (pkg == null || pkg.length() == 0) ? "ai.mainfunc.genspark.pro" : pkg;
        return new String[]{
            "/data/local/tmp/" + FILE_NAME,
            "/sdcard/Android/data/" + p + "/files/" + FILE_NAME,
            "/data/data/" + p + "/files/" + FILE_NAME,
            "/sdcard/Download/" + FILE_NAME
        };
    }
}
