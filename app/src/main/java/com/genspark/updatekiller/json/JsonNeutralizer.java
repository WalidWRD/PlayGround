package com.genspark.updatekiller.json;

import com.genspark.updatekiller.Config;
import com.genspark.updatekiller.Guard;
import java.util.Iterator;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/* loaded from: classes.dex */
public final class JsonNeutralizer {
    private JsonNeutralizer() {
    }

    public static boolean apply(final JSONObject jSONObject) {
        if (jSONObject == null) {
            return false;
        }
        final Config config = Config.get();
        final boolean[] zArr = {false};
        Guard.run("JsonObject", new Guard.Action() { // from class: com.genspark.updatekiller.json.JsonNeutralizer$$ExternalSyntheticLambda0
            @Override // com.genspark.updatekiller.Guard.Action
            public final void run() throws Throwable {
                JsonNeutralizer.walk(jSONObject, config, zArr);
            }
        });
        return zArr[0];
    }

    /* JADX INFO: Access modifiers changed from: private */
    public static void walk(JSONObject jSONObject, Config config, boolean[] zArr) {
        JSONArray names = jSONObject.names();
        if (names == null) {
            return;
        }
        for (int i = 0; i < names.length(); i++) {
            String optString = names.optString(i, null);
            if (optString != null) {
                try {
                    if (config.hasKey(optString)) {
                        if (isBool(optString)) {
                            if (jSONObject.optBoolean(optString, false)) {
                                jSONObject.put(optString, false);
                                zArr[0] = true;
                            }
                        } else if (isInt(optString)) {
                            if (jSONObject.optInt(optString, 0) != 0) {
                                jSONObject.put(optString, 0);
                                zArr[0] = true;
                            }
                        } else if (jSONObject.optString(optString, "").length() > 0) {
                            jSONObject.put(optString, "");
                            zArr[0] = true;
                        }
                    } else {
                        JSONObject optJSONObject = jSONObject.optJSONObject(optString);
                        if (optJSONObject != null) {
                            walk(optJSONObject, config, zArr);
                        }
                        JSONArray optJSONArray = jSONObject.optJSONArray(optString);
                        if (optJSONArray != null) {
                            walk(optJSONArray, config, zArr);
                        }
                    }
                } catch (JSONException unused) {
                }
            }
        }
    }

    private static void walk(JSONArray jSONArray, Config config, boolean[] zArr) {
        for (int i = 0; i < jSONArray.length(); i++) {
            try {
                JSONObject optJSONObject = jSONArray.optJSONObject(i);
                if (optJSONObject != null) {
                    walk(optJSONObject, config, zArr);
                }
                JSONArray optJSONArray = jSONArray.optJSONArray(i);
                if (optJSONArray != null) {
                    walk(optJSONArray, config, zArr);
                }
            } catch (Throwable unused) {
            }
        }
    }

    public static String applyToText(final String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        final Config config = Config.get();
        return (String) Guard.value("JsonText", new Guard.Func() { // from class: com.genspark.updatekiller.json.JsonNeutralizer$$ExternalSyntheticLambda1
            @Override // com.genspark.updatekiller.Guard.Func
            public final Object run() throws Throwable {
                return JsonNeutralizer.lambda$applyToText$1(config, str);
            }
        }, str);
    }

    static /* synthetic */ String lambda$applyToText$1(Config config, String str) throws Throwable {
        boolean z;
        Iterator<String> it = config.keys.iterator();
        while (true) {
            if (!it.hasNext()) {
                z = false;
                break;
            }
            String next = it.next();
            if (next != null && str.contains("\"" + next + "\"")) {
                z = true;
                break;
            }
        }
        if (!z) {
            return str;
        }
        for (String str2 : config.keys) {
            if (str2 != null) {
                String quote = Pattern.quote(str2);
                str = str.replaceAll("(\"" + quote + "\"\\s*:\\s*)true", "$1false").replaceAll("(\"" + quote + "\"\\s*:\\s*)[1-9][0-9]*", "$10").replaceAll("(\"" + quote + "\"\\s*:\\s*)\"[^\"]*\"", "$1\"\"");
            }
        }
        return str;
    }

    public static boolean changedText(String str, String str2) {
        return (str == null || str2 == null || str.equals(str2)) ? false : true;
    }

    public static boolean isBool(String str) {
        return "forceUpgrade".equals(str) || "forceUpgradeD".equals(str) || "directUpgrade".equals(str);
    }

    public static boolean isInt(String str) {
        return "minAppVersionCode".equals(str);
    }

    public static boolean isStr(String str) {
        return "minAppRequireAppVersion".equals(str) || "requiresAppVersion".equals(str);
    }
}
