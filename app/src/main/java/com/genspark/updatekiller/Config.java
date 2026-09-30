package com.genspark.updatekiller;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/* loaded from: classes.dex */
public final class Config {
    private static volatile Config INSTANCE;
    private static volatile long lastLoadMs;
    public boolean enabled = true;
    public boolean spoofVersion = true;
    public int spoofVersionCode = 9999999;
    public String spoofVersionName = "99.9.9";
    public boolean neutralizeJson = true;
    public boolean blockStoreIntent = true;
    public boolean prefsHook = true;
    public boolean dialogWatch = false;
    public boolean playCore = true;
    public boolean nativeProbe = true;
    public boolean verbose = true;
    public boolean eventLog = true;
    public boolean adsBlock = true;
    public boolean blutterTrace = true;
    public boolean blockWarnings = true;
    public boolean bypassOfficialRedirect = true;
    public boolean antiCrash = true;
    public boolean bypassLogin = false;
    public boolean autoLogin = false;
    public boolean spoofGoogleSignIn = true;
    public boolean blockLoginBanner = true;
    public boolean forceSubscribed = true;
    public boolean forceCredits = true;
    public boolean ultraFree = true;
    public List<String> keys = Arrays.asList("forceUpgrade", "forceUpgradeD", "directUpgrade", "minAppVersionCode", "minAppRequireAppVersion", "requiresAppVersion");
    public String storeRedirectUrl = BuildInfo.HOMEPAGE;
    public boolean blockWebViewStore = true;
    public int killSwitchThreshold = 20;
    public String loginEmail = "";
    public String loginPassword = "";
    public int autoLoginDelayMs = 2500;

    public static Config get() {
        Config config = INSTANCE;
        if (config == null) {
            synchronized (Config.class) {
                if (INSTANCE == null) {
                    INSTANCE = new Config();
                }
                config = INSTANCE;
            }
        }
        long currentTimeMillis = System.currentTimeMillis();
        if (currentTimeMillis - lastLoadMs > 5000) {
            config.reload();
            lastLoadMs = currentTimeMillis;
        }
        return config;
    }

    public synchronized void reload() {
        String[] strArr = {"/data/local/tmp/genspark_updatekiller.json", "/sdcard/Download/genspark_updatekiller.json"};
        for (int i = 0; i < 2; i++) {
            String str = strArr[i];
            try {
                File file = new File(str);
                if (file.exists() && file.canRead()) {
                    applyFromText(readFile(file));
                    UxLog.i("Config: loaded from " + str);
                    return;
                }
            } catch (Throwable unused) {
            }
        }
    }

    public boolean hasKey(String str) {
        List<String> list;
        if (str != null && (list = this.keys) != null) {
            Iterator<String> it = list.iterator();
            while (it.hasNext()) {
                if (str.equals(it.next())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String readFile(File file) throws Throwable {
        FileInputStream fileInputStream = new FileInputStream(file);
        try {
            int length = (int) file.length();
            byte[] bArr = new byte[length];
            int i = 0;
            while (i < length) {
                int read = fileInputStream.read(bArr, i, length - i);
                if (read == -1) {
                    break;
                }
                i += read;
            }
            return new String(bArr, 0, i, "UTF-8");
        } finally {
            try {
                fileInputStream.close();
            } catch (Throwable unused) {
            }
        }
    }

    private void applyFromText(String str) throws Throwable {
        JSONArray optJSONArray;
        if (str == null || str.isEmpty()) {
            return;
        }
        JSONObject jSONObject = new JSONObject(str);
        if (jSONObject.has("enabled")) {
            this.enabled = jSONObject.optBoolean("enabled", this.enabled);
        }
        if (jSONObject.has("spoofVersion")) {
            this.spoofVersion = jSONObject.optBoolean("spoofVersion", this.spoofVersion);
        }
        if (jSONObject.has("spoofVersionCode")) {
            this.spoofVersionCode = jSONObject.optInt("spoofVersionCode", this.spoofVersionCode);
        }
        if (jSONObject.has("spoofVersionName")) {
            this.spoofVersionName = jSONObject.optString("spoofVersionName", this.spoofVersionName);
        }
        if (jSONObject.has("neutralizeJson")) {
            this.neutralizeJson = jSONObject.optBoolean("neutralizeJson", this.neutralizeJson);
        }
        if (jSONObject.has("blockStoreIntent")) {
            this.blockStoreIntent = jSONObject.optBoolean("blockStoreIntent", this.blockStoreIntent);
        }
        if (jSONObject.has("prefsHook")) {
            this.prefsHook = jSONObject.optBoolean("prefsHook", this.prefsHook);
        }
        if (jSONObject.has("dialogWatch")) {
            this.dialogWatch = jSONObject.optBoolean("dialogWatch", this.dialogWatch);
        }
        if (jSONObject.has("playCore")) {
            this.playCore = jSONObject.optBoolean("playCore", this.playCore);
        }
        if (jSONObject.has("nativeProbe")) {
            this.nativeProbe = jSONObject.optBoolean("nativeProbe", this.nativeProbe);
        }
        if (jSONObject.has("verbose")) {
            this.verbose = jSONObject.optBoolean("verbose", this.verbose);
        }
        if (jSONObject.has("eventLog")) {
            this.eventLog = jSONObject.optBoolean("eventLog", this.eventLog);
        }
        if (jSONObject.has("adsBlock")) {
            this.adsBlock = jSONObject.optBoolean("adsBlock", this.adsBlock);
        }
        if (jSONObject.has("blutterTrace")) {
            this.blutterTrace = jSONObject.optBoolean("blutterTrace", this.blutterTrace);
        }
        if (jSONObject.has("blockWarnings")) {
            this.blockWarnings = jSONObject.optBoolean("blockWarnings", this.blockWarnings);
        }
        if (jSONObject.has("bypassOfficialRedirect")) {
            this.bypassOfficialRedirect = jSONObject.optBoolean("bypassOfficialRedirect", this.bypassOfficialRedirect);
        }
        if (jSONObject.has("antiCrash")) {
            this.antiCrash = jSONObject.optBoolean("antiCrash", this.antiCrash);
        }
        if (jSONObject.has("bypassLogin")) {
            this.bypassLogin = jSONObject.optBoolean("bypassLogin", this.bypassLogin);
        }
        if (jSONObject.has("autoLogin")) {
            this.autoLogin = jSONObject.optBoolean("autoLogin", this.autoLogin);
        }
        if (jSONObject.has("spoofGoogleSignIn")) {
            this.spoofGoogleSignIn = jSONObject.optBoolean("spoofGoogleSignIn", this.spoofGoogleSignIn);
        }
        if (jSONObject.has("blockLoginBanner")) {
            this.blockLoginBanner = jSONObject.optBoolean("blockLoginBanner", this.blockLoginBanner);
        }
        if (jSONObject.has("forceSubscribed")) {
            this.forceSubscribed = jSONObject.optBoolean("forceSubscribed", this.forceSubscribed);
        }
        if (jSONObject.has("forceCredits")) {
            this.forceCredits = jSONObject.optBoolean("forceCredits", this.forceCredits);
        }
        if (jSONObject.has("ultraFree")) {
            this.ultraFree = jSONObject.optBoolean("ultraFree", this.ultraFree);
        }
        if (jSONObject.has("storeRedirectUrl")) {
            this.storeRedirectUrl = jSONObject.optString("storeRedirectUrl", this.storeRedirectUrl);
        }
        if (jSONObject.has("blockWebViewStore")) {
            this.blockWebViewStore = jSONObject.optBoolean("blockWebViewStore", this.blockWebViewStore);
        }
        if (jSONObject.has("killSwitchThreshold")) {
            this.killSwitchThreshold = jSONObject.optInt("killSwitchThreshold", this.killSwitchThreshold);
        }
        if (jSONObject.has("loginEmail")) {
            this.loginEmail = jSONObject.optString("loginEmail", this.loginEmail);
        }
        if (jSONObject.has("loginPassword")) {
            this.loginPassword = jSONObject.optString("loginPassword", this.loginPassword);
        }
        if (jSONObject.has("autoLoginDelayMs")) {
            this.autoLoginDelayMs = jSONObject.optInt("autoLoginDelayMs", this.autoLoginDelayMs);
        }
        if (jSONObject.has("keys") && (optJSONArray = jSONObject.optJSONArray("keys")) != null) {
            ArrayList arrayList = new ArrayList();
            for (int i = 0; i < optJSONArray.length(); i++) {
                arrayList.add(optJSONArray.optString(i, ""));
            }
            this.keys = Collections.unmodifiableList(arrayList);
        }
        Guard.setThreshold(this.killSwitchThreshold);
    }
}
