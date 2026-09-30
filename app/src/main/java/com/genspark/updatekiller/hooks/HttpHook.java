package com.genspark.updatekiller.hooks;

import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Guard;
import com.genspark.updatekiller.Reflect;
import com.genspark.updatekiller.SubscriptionBridge;
import com.genspark.updatekiller.UpdateEventLogger;
import com.genspark.updatekiller.UxLog;
import com.genspark.updatekiller.json.JsonNeutralizer;
import de.robv.android.xposed.XC_MethodHook;
import org.json.JSONArray;
import org.json.JSONObject;

/* Server-verification bypass lives here: every HTTP body is scanned for
 * subscription payloads and forced to lifetime BEFORE the app parses it. */
public final class HttpHook {
    private HttpHook() {
    }

    public static int install(ClassLoader classLoader) {
        // NOTE: gated by neutralizeJson OR forceSubscribed OR forceCredits —
        // server bypass must still run when only subscription/credits is enabled.
        if (!Config.get().neutralizeJson && !Config.get().forceSubscribed && !Config.get().forceCredits) {
            UxLog.i("HttpHook: disabled");
            return 0;
        }
        Class<?> findClass = Reflect.findClass("okhttp3.ResponseBody", classLoader);
        int hookAllNamed = findClass != null ? 0 + Reflect.hookAllNamed(findClass, "string", new AnonymousClass1()) : 0;
        Class<?> findClass2 = Reflect.findClass("okio.Buffer", classLoader);
        if (findClass2 != null) {
            hookAllNamed += Reflect.hookAllNamed(findClass2, "readUtf8", new AnonymousClass2());
        }
        // Retrofit/Gson path: some builds read via ResponseBody.charStream/bytes.
        if (findClass != null) {
            hookAllNamed += Reflect.hookAllNamed(findClass, "charStream", new AnonymousClass1());
            hookAllNamed += Reflect.hookAllNamed(findClass, "byteString", new AnonymousClass1());
            hookAllNamed += Reflect.hookAllNamed(findClass, "bytes", new AnonymousClass1());
        }
        UxLog.i("HttpHook: " + hookAllNamed + " hook(s)");
        return hookAllNamed;
    }

    /* renamed from: com.genspark.updatekiller.hooks.HttpHook$1 */
    static class AnonymousClass1 extends XC_MethodHook {
        AnonymousClass1() {
        }

        @Override
        protected void afterHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("HttpHook.ResponseBody.string", new Guard.Action() {
                @Override
                public void run() throws Throwable {
                    HttpHook.rewrite(methodHookParam);
                }
            });
        }
    }

    /* renamed from: com.genspark.updatekiller.hooks.HttpHook$2 */
    static class AnonymousClass2 extends XC_MethodHook {
        AnonymousClass2() {
        }

        @Override
        protected void afterHookedMethod(final XC_MethodHook.MethodHookParam methodHookParam) {
            Guard.run("HttpHook.Buffer.readUtf8", new Guard.Action() {
                @Override
                public void run() throws Throwable {
                    HttpHook.rewrite(methodHookParam);
                }
            });
        }
    }

    static void rewrite(XC_MethodHook.MethodHookParam methodHookParam) {
        Object result = methodHookParam.getResult();
        if (!(result instanceof String)) {
            return;
        }
        String str = (String) result;
        if (str.isEmpty()) {
            return;
        }
        boolean touched = false;
        String cur = str;

        // 1) update-config neutralizer (existing behaviour)
        if (Config.get().neutralizeJson) {
            String patched = JsonNeutralizer.applyToText(cur);
            if (JsonNeutralizer.changedText(cur, patched)) {
                cur = patched;
                touched = true;
                UxLog.i("HttpHook: neutralized update fields (chars " + str.length() + ")");
            }
        }

        // 2) SERVER SUBSCRIPTION/CREDIT BYPASS — lifetime + infinity
        if (Config.get().forceSubscribed || Config.get().forceCredits || Config.get().ultraFree) {
            // 2a) raw-text pass (fast, catches status/plan/booleans)
            String textPatched = SubscriptionBridge.patchServerJsonText(cur);
            if (!textPatched.equals(cur)) {
                cur = textPatched;
                touched = true;
            }
            // 2b) parsed-JSON pass (catches expiry timestamps + nested payloads)
            try {
                String trimmed = cur.trim();
                if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
                        || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                    if (trimmed.startsWith("{")) {
                        JSONObject o = new JSONObject(cur);
                        if (SubscriptionBridge.applyLifetimeToJson(o)) {
                            cur = o.toString();
                            touched = true;
                        }
                    } else {
                        JSONArray a = new JSONArray(cur);
                        boolean ch = false;
                        for (int i = 0; i < a.length(); i++) {
                            JSONObject item = a.optJSONObject(i);
                            if (item != null && SubscriptionBridge.applyLifetimeToJson(item)) {
                                ch = true;
                            }
                        }
                        if (ch) {
                            cur = a.toString();
                            touched = true;
                        }
                    }
                }
            } catch (Throwable t) {
                Guard.record("HttpHook.subJson", t);
            }
        }

        if (touched && !cur.equals(str)) {
            methodHookParam.setResult(cur);
            UpdateEventLogger.log("http", "server response patched (sub=lifetime, chars " + str.length() + ")");
        }
    }
}
