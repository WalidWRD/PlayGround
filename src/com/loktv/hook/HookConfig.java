package com.loktv.hook;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Hot configuration: a plain key=value file. Editing it and restarting the app
 * changes behaviour without re-patching the APK. Missing file => safe defaults.
 */
public final class HookConfig {

    public boolean vip              = true;
    public boolean skipUpdate       = true;
    public boolean validCollection  = true;
    public boolean videoData        = true;
    public boolean disablePopup     = true;
    public boolean antiDetect       = true;
    public boolean antiVpn          = true;
    public boolean floatingView     = true;
    public boolean genericScanner   = true;
    public boolean adsBlock         = true;
    public boolean trackerBlock     = true;
    public boolean updateDialogBlock = true;
    public boolean hideVipUi        = true;
    public boolean vipItem          = true;
    public boolean licenseBypass    = true;
    /** v3.1.0: invoke v1-style void installers (they self-install hooks). */
    public boolean legacyInvoke     = true;
    /** v3.2.0: visible proof toast on launch (toast=0 to disable). */
    public boolean toast            = true;
    /** -1 = keep app behaviour, 0 = portrait, 1 = landscape */
    public int     rotation         = -1;
    public boolean debugVerbose     = false;

    public static HookConfig load(Context ctx, String pkg) {
        HookConfig c = new HookConfig();
        Map<String, String> map = new HashMap<String, String>();
        File f = file(ctx, pkg);
        if (f == null) return c;
        if (!f.exists()) {
            writeDefault(f);
            return c;
        }
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(f));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0 || line.startsWith("#") || line.startsWith("//")) continue;
                int i = line.indexOf('=');
                if (i <= 0) continue;
                map.put(line.substring(0, i).trim().toLowerCase(),
                        line.substring(i + 1).trim().toLowerCase());
            }
        } catch (Throwable t) {
            Log.w("config read failed: " + Log.describe(t));
        } finally {
            try { if (br != null) br.close(); } catch (Throwable ignored) {}
        }
        c.vip             = bool(map, "vip", c.vip);
        c.skipUpdate      = bool(map, "skip_update", c.skipUpdate);
        c.validCollection = bool(map, "valid_collection", c.validCollection);
        c.videoData       = bool(map, "video_data", c.videoData);
        c.disablePopup    = bool(map, "disable_popup", c.disablePopup);
        c.antiDetect      = bool(map, "anti_detect", c.antiDetect);
        c.antiVpn         = bool(map, "anti_vpn", c.antiVpn);
        c.floatingView    = bool(map, "floating_view", c.floatingView);
        c.genericScanner  = bool(map, "generic_scanner", c.genericScanner);
        c.adsBlock        = bool(map, "ads_block", c.adsBlock);
        c.trackerBlock    = bool(map, "tracker_block", c.trackerBlock);
        c.updateDialogBlock = bool(map, "update_dialog", c.updateDialogBlock);
        if (!map.containsKey("update_dialog")) {
            c.updateDialogBlock = bool(map, "update_dialog_block", c.updateDialogBlock);
        }
        c.hideVipUi       = bool(map, "hide_vip_ui", c.hideVipUi);
        c.vipItem         = bool(map, "vip_item", c.vipItem);
        c.licenseBypass   = bool(map, "license_bypass", c.licenseBypass);
        c.legacyInvoke    = bool(map, "legacy_invoke", c.legacyInvoke);
        c.toast           = bool(map, "toast", c.toast);
        c.debugVerbose    = bool(map, "debug", c.debugVerbose);
        try {
            String r = map.get("rotation");
            if (r != null) {
                int v = Integer.parseInt(r.trim());
                if (v == -1 || v == 0 || v == 1) c.rotation = v;
            }
        } catch (Throwable ignored) {}
        return c;
    }

    private static boolean bool(Map<String, String> m, String k, boolean def) {
        String v = m.get(k);
        if (v == null) return def;
        return v.equals("1") || v.equals("true") || v.equals("on") || v.equals("yes") || v.equals("enabled");
    }

    /** v3.1.0: absolute config path for the log (tells the user which file to edit). */
    public static String pathOf(Context ctx, String pkg) {
        try {
            File f = file(ctx, pkg);
            return f == null ? "null" : f.getAbsolutePath();
        } catch (Throwable t) {
            return "unknown";
        }
    }

    private static File file(Context ctx, String pkg) {
        try {
            File dir = ctx != null ? ctx.getFilesDir() : null;
            if (dir == null) dir = new File("/sdcard/Android/data/" + pkg + "/files");
            if (!dir.exists()) dir.mkdirs();
            return new File(dir, "loktv_hook.conf");
        } catch (Throwable t) {
            return null;
        }
    }

    private static void writeDefault(File f) {
        FileWriter w = null;
        try {
            w = new FileWriter(f, false);
            w.write("# " + ModuleInfo.MODULE_NAME + " v" + ModuleInfo.VERSION + " - hot config\n");
            w.write("# 1/true = enabled, 0/false = disabled. Restart the app after editing.\n");
            w.write("vip=1\nskip_update=1\nvalid_collection=1\nvideo_data=1\n");
            w.write("disable_popup=1\nanti_detect=1\nanti_vpn=1\nfloating_view=1\n");
            w.write("generic_scanner=1\nads_block=1\ntracker_block=1\nupdate_dialog=1\n");
            w.write("hide_vip_ui=1\nvip_item=1\nlicense_bypass=1\nlegacy_invoke=1\ntoast=1\n");
            w.write("# rotation: -1 keep app default, 0 portrait, 1 landscape\n");
            w.write("rotation=-1\ndebug=0\n");
            w.flush();
        } catch (Throwable ignored) {
        } finally {
            try { if (w != null) w.close(); } catch (Throwable ignored) {}
        }
    }
}
