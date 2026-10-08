package com.loktv.hook;

import java.lang.reflect.Method;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;

/**
 * The hook catalogue v2.1.0. Every entry is reflection driven and defensive:
 * a failing hook is counted and logged, never propagated (no crash can break
 * the host app). Uses lpparam.classLoader + dex-wide search, no hard names.
 */
public final class Features {

    public interface Counter {
        void ok(String what);
        void fail(String what, Throwable t);
    }

    /** Class-name hints for the fast path (newest -> oldest known naming). */
    private static final String[] CLS_USER_STATUS = {
            "com.novan.morpha.UserStatus",
            "com.novan.UserStatus"
    };
    private static final String[] CLS_ANTI_VPN   = {"com.novan.morpha.AntiVPN", "com.novan.AntiVPN"};
    private static final String[] CLS_FLOATING   = {"com.novan.morpha.FloatingView", "com.novan.FloatingView"};
    private static final String[] CLS_ROTATION   = {"com.novan.morpha.GetRotation", "com.novan.GetRotation"};

    private static final String[] PKG_PREFIXES = {
            "com.novan.morpha", "com.novan"
    };

    /**
     * v3.1.0: v1-style VOID installers. Smali evidence: isVip/isSkipUpdate/
     * isValidCollection/isVideoDataEnabled/DisableSomePopup/isDisable/isVipItem/
     * init/init2/Load are "()V" methods that SELF-INSTALL the real hooks when
     * CALLED. Replacing them with no-op (as v2.x did) DISABLES the feature.
     * So: INVOKE void installers, REPLACE boolean gates. These exact names
     * are also skipped in every generic replace loop.
     */
    private static final String[] INSTALLER_NAMES = {
            "isvip", "isskipupdate", "isvalidcollection", "isvideodataenabled",
            "disablesomepopup", "isdisable", "isvipitem", "init", "init2", "load"
    };

    private static boolean isInstallerName(String lower) {
        if (lower == null) return false;
        for (String k : INSTALLER_NAMES) {
            if (lower.equals(k)) return true;
        }
        return false;
    }

    /**
     * v3.1.0: third-party ad SDK packages. The v3.0.0 field report
     * ("no crash, but ads not blocked") was caused by scanning ONLY the
     * target package: real ad SDKs live in THEIR OWN packages, so the
     * prefix scan found novan classes and the unfiltered fallback never
     * fired. These prefixes are scanned alongside the target package.
     */
    private static final String[] AD_SDK_PREFIXES = {
            "com.google.android.gms.ads", "com.google.ads",
            "com.facebook.ads", "com.facebook.audience",
            "com.unity3d.ads", "com.applovin", "com.inmobi",
            "com.mopub", "com.bytedance.sdk.openadsdk", "com.vungle",
            "com.ironsource", "com.amazon.device.ads", "com.smaato",
            "com.mintegral", "com.appnext", "com.chartboost",
            "com.tapjoy", "com.adcolony",
            "com.huawei.hms.ads", "com.xiaomi.ad",
            "com.qq.e", "com.baidu.mobads"
    };

    /** v3.1.0: third-party analytics/tracker SDK packages (same reason). */
    private static final String[] TRACKER_SDK_PREFIXES = {
            "com.umeng", "com.appsflyer", "com.adjust",
            "com.tencent.bugly", "com.bugly", "com.flurry",
            "com.google.firebase",
            "com.google.android.gms.analytics",
            "com.google.android.gms.measurement",
            "com.sensorsdata", "com.growingio", "com.talkingdata",
            "com.yandex.metrica", "com.amplitude", "com.mixpanel",
            "com.branch", "com.onesignal", "com.igexin",
            "com.huawei.hms.analytics", "com.xiaomi.mipush",
            "com.vivo.push", "com.heytap", "com.oppo.push",
            "com.meizu.push", "io.sentry"
    };

    // ------------------------------------------------------------------ F01-F05

    /**
     * v3.1.0 rework: each gate is EITHER a boolean (replace -> true) OR a
     * void installer (INVOKE it now - it self-installs the real hooks).
     * v2.x replaced void installers with no-op, which DISABLED the feature.
     */
    public static int userStatus(ClassLoader cl, HookConfig cfg, Counter c) {
        int n = 0;
        Class<?> cls = Reflect.firstClass(cl, CLS_USER_STATUS);
        if (cls == null) cls = ClassScanner.bySimpleName(cl, "UserStatus", PKG_PREFIXES);
        if (cls == null) {
            c.fail("UserStatus class not found", null);
            return 0;
        }
        c.ok("UserStatus = " + cls.getName());

        if (cfg.vip)             n += gateTrue(cls, "isVip", c);
        if (cfg.skipUpdate) {
            n += gateTrue(cls, "isSkipUpdate", c);
            // v2.1.0: force-update gates have inverted polarity -> must be FALSE
            n += forceFalseAnyName(cls, c,
                    "isForceUpdate", "isNeedUpdate", "needUpdate",
                    "hasUpdate", "hasNewVersion", "isUpdateAvailable",
                    "isMustUpdate", "shouldForceUpdate");
            n += noopAnyName(cls, c, "checkUpdate", "checkForUpdate", "requestUpdate");
        }
        if (cfg.validCollection) n += gateTrue(cls, "isValidCollection", c);
        if (cfg.videoData)       n += gateTrue(cls, "isVideoDataEnabled", c);
        if (cfg.disablePopup)    n += gateNoop(cls, "DisableSomePopup", c);
        // v2.1.0: numeric VIP level / expiry hardening (best effort)
        if (cfg.vip) {
            n += forceIntIfPresent(cls, c,
                    new String[]{"getVipLevel", "getLevel", "getVipType", "getMemberLevel"}, 999);
            n += forceLongIfPresent(cls, c,
                    new String[]{"getExpireTime", "getVipExpire", "getExpiry", "getEndTime"},
                    4102444800000L);
        }
        return n;
    }

    /** F06 - mod/tamper/root/emulator/signature detection neutralizer. */
    public static int antiDetect(ClassLoader cl, HookConfig cfg, Counter c) {
        if (!cfg.antiDetect) return 0;
        int n = 0;
        final String[] KEYS = {
            "modded", "isroot", "rooted", "checkroot", "tamper", "ishacked",
            "hacked", "checksignature", "signature", "verify", "integrity",
            "safetynet", "playintegrity", "emulator", "isdebug", "debugger",
            "xposed", "frida", "magisk", "substrate", "hookcheck", "enlarged"
        };
        List<Class<?>> all = ClassScanner.classes(cl, PKG_PREFIXES, 4000);
        for (Class<?> cls : all) {
            Method[] ms;
            try { ms = cls.getDeclaredMethods(); } catch (Throwable t) { continue; }
            for (Method m : ms) {
                String nm = safeLower(m.getName());
                if (isInstallerName(nm)) continue; // v3.1.0: never neuter installers
                if (!contains(KEYS, nm)) continue;
                if (Reflect.isAbstract(m)) continue;
                if (Reflect.returnsBoolean(m)) n += replace(m, Boolean.FALSE, c);
                else if (Reflect.returnsString(m)) n += replace(m, "", c);
                else n += replace(m, null, c);
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ F07

    public static int antiVpn(ClassLoader cl, HookConfig cfg, Counter c) {
        if (!cfg.antiVpn) return 0;
        int n = 0;
        Class<?> cls = Reflect.firstClass(cl, CLS_ANTI_VPN);
        if (cls != null) {
            Method[] ms;
            try { ms = cls.getDeclaredMethods(); } catch (Throwable t) { ms = new Method[0]; }
            for (Method m : ms) {
                if (Reflect.isAbstract(m)) continue;
                if (Reflect.returnsBoolean(m)) n += replace(m, Boolean.FALSE, c);
                else if (Reflect.returnsString(m)) n += replace(m, "", c);
            }
            c.ok("AntiVPN class hooked = " + cls.getName());
        }
        // generic: any class whose name mentions vpn/proxy/dns
        for (Class<?> k : ClassScanner.classes(cl, PKG_PREFIXES, 4000)) {
            String simple = safeLower(k.getName());
            if (!(simple.contains("vpn") || simple.contains("proxy")
                    || simple.contains("detector") && simple.contains("net"))) continue;
            try {
                for (Method m : k.getDeclaredMethods()) {
                    if (Reflect.isAbstract(m) || !Reflect.isNoArg(m)) continue;
                    if (Reflect.returnsBoolean(m)) n += replace(m, Boolean.FALSE, c);
                }
            } catch (Throwable ignored) {}
        }
        // generic: method-name heuristic across target package
        for (Class<?> k : ClassScanner.classes(cl, PKG_PREFIXES, 4000)) {
            try {
                for (Method m : k.getDeclaredMethods()) {
                    if (Reflect.isAbstract(m) || !Reflect.isNoArg(m)
                            || !Reflect.returnsBoolean(m)) continue;
                    String nm = safeLower(m.getName());
                    if (nm.contains("vpn") || nm.contains("proxy")
                            || nm.contains("isusingvpn") || nm.contains("vpndetect")) {
                        n += replace(m, Boolean.FALSE, c);
                    }
                }
            } catch (Throwable ignored) {}
        }
        return n;
    }

    // ------------------------------------------------------------------ F08

    public static int floatingView(ClassLoader cl, HookConfig cfg, Counter c) {
        if (!cfg.floatingView) return 0;
        int n = 0;
        for (String cn : CLS_FLOATING) {
            Class<?> cls = Reflect.findClass(cl, cn);
            if (cls == null) continue;
            // v3.1.0: isDisable()V is an installer - INVOKE it, don't neuter it.
            try {
                Method inst = Reflect.methodNoArg(cls, "isDisable");
                if (inst != null && Reflect.returnsVoid(inst) && invokeInstaller(inst, c) > 0) {
                    c.ok("FloatingView neutralised = " + cls.getName());
                    continue;
                }
            } catch (Throwable ignored) {}
            try {
                for (Method m : cls.getDeclaredMethods()) {
                    if (Reflect.isAbstract(m)) continue;
                    String nm = safeLower(m.getName());
                    if (isInstallerName(nm)) continue;
                    if (nm.contains("disable") || nm.contains("show") || nm.contains("add")
                            || nm.contains("create") || nm.contains("start")) {
                        n += replace(m, null, c);
                    }
                }
            } catch (Throwable ignored) {}
            c.ok("FloatingView neutralised = " + cls.getName());
        }
        // v2.1.0: fallback by simple name (obfuscated builds)
        try {
            Class<?> fb = ClassScanner.bySimpleName(cl, "FloatingView", PKG_PREFIXES);
            if (fb != null && Reflect.findClass(cl, CLS_FLOATING[0]) == null
                    && Reflect.findClass(cl, CLS_FLOATING[1]) == null) {
                try {
                    Method inst = Reflect.methodNoArg(fb, "isDisable");
                    if (inst != null && Reflect.returnsVoid(inst) && invokeInstaller(inst, c) > 0) {
                        c.ok("FloatingView neutralised (fallback) = " + fb.getName());
                        return n;
                    }
                } catch (Throwable ignored) {}
                for (Method m : fb.getDeclaredMethods()) {
                    if (Reflect.isAbstract(m)) continue;
                    String nm = safeLower(m.getName());
                    if (isInstallerName(nm)) continue;
                    if (nm.contains("disable") || nm.contains("show") || nm.contains("add")
                            || nm.contains("create") || nm.contains("start")) {
                        n += replace(m, null, c);
                    }
                }
                c.ok("FloatingView neutralised (fallback) = " + fb.getName());
            }
        } catch (Throwable ignored) {}
        return n;
    }

    // ------------------------------------------------------------------ F09

    public static int rotation(ClassLoader cl, HookConfig cfg, Counter c) {
        if (cfg.rotation < 0) return 0;
        final Integer value = Integer.valueOf(cfg.rotation);
        int n = 0;
        for (String cn : CLS_ROTATION) {
            Class<?> cls = Reflect.findClass(cl, cn);
            if (cls == null) continue;
            try {
                for (Method m : cls.getDeclaredMethods()) {
                    if (Reflect.isAbstract(m)) continue;
                    if (Reflect.returnsInt(m) || m.getReturnType() == Object.class) {
                        n += replace(m, value, c);
                    }
                }
            } catch (Throwable ignored) {}
        }
        for (Class<?> k : ClassScanner.classes(cl, PKG_PREFIXES, 4000)) {
            try {
                for (Method m : k.getDeclaredMethods()) {
                    String nm = safeLower(m.getName());
                    if (!Reflect.isNoArg(m) || Reflect.isAbstract(m)) continue;
                    if (!(nm.equals("getrotation") || nm.equals("getscreenorientation")
                            || nm.equals("getorientation"))) continue;
                    n += replace(m, value, c);
                }
            } catch (Throwable ignored) {}
        }
        c.ok("rotation forced -> " + cfg.rotation);
        return n;
    }

    // ------------------------------------------------------------------ F10

    /**
     * Generic dex-wide resolver: turns any no-arg boolean gate whose name looks
     * like a permission/feature flag into a positive answer, and any
     * "isModded/isVpn/isAd" style gate into a negative one. This is what keeps
     * the module working after the vendor renames or obfuscates its classes.
     */
    public static int genericScanner(ClassLoader cl, HookConfig cfg, Counter c) {
        if (!cfg.genericScanner) return 0;
        final String[] POSITIVE = {
                "isvip", "ispremium", "issvip", "ispro", "ispaid", "issubscribed",
                "issubscription", "isvalid", "isunlocked", "isfull", "isvalidcollection",
                "isvideodataenabled", "isskipupdate", "skipupdate", "isautologin",
                "isactivated", "isregistered", "islifetime", "ismember", "haspremium",
                "hasvip", "unlock", "enablevip", "islogin", "isloggedin"
        };
        final String[] NEGATIVE = {
                "ismodded", "ishacked", "istampered", "isdebug", "isemulator",
                "isvpn", "isproxy", "isad", "isads", "showad", "isroot", "rooted",
                "isxposed", "isfrida", "ismagisk", "ischeck", "needupdate",
                "forceupdate", "hasupdate", "isupdate"
        };
        final String[] VIP_INT = {"vip", "level", "membertype", "memberlevel"};
        int n = 0;
        List<Class<?>> all = ClassScanner.classes(cl, PKG_PREFIXES, 8000);
        for (Class<?> cls : all) {
            Method[] ms;
            try { ms = cls.getDeclaredMethods(); } catch (Throwable t) { continue; }
            for (Method m : ms) {
                if (Reflect.isAbstract(m) || !Reflect.isNoArg(m)) continue;
                String nm = safeLower(m.getName());
                if (isInstallerName(nm)) continue; // v3.1.0: installers are invoked, not replaced
                if (nm.startsWith("set") || nm.startsWith("add") || nm.startsWith("remove")) continue;
                if (Reflect.returnsBoolean(m)) {
                    if (contains(POSITIVE, nm)) n += replace(m, Boolean.TRUE, c);
                    else if (contains(NEGATIVE, nm)) {
                        // update gates must be FALSE, vip gates TRUE - NEGATIVE already FALSE
                        if (nm.contains("update")) n += replace(m, Boolean.FALSE, c);
                        else n += replace(m, Boolean.FALSE, c);
                    }
                } else if (Reflect.returnsInt(m)) {
                    if (contains(VIP_INT, nm) || nm.contains("vip") || nm.contains("coin")
                            || nm.contains("point") || nm.contains("credit")) {
                        n += replace(m, Integer.valueOf(9999), c);
                    }
                } else if (Reflect.returnsLong(m)) {
                    if (nm.contains("expire") || nm.contains("vip") || nm.contains("coin")) {
                        n += replace(m, Long.valueOf(4102444800000L), c);
                    }
                }
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ F15

    /** F15 - ads / splash / banner / reward / interstitial neutralizer.
     *  v3.1.0: scans target package AND third-party ad SDK packages. */
    public static int adsBlock(ClassLoader cl, HookConfig cfg, Counter c) {
        if (!cfg.adsBlock) return 0;
        int n = 0;
        List<Class<?>> all = ClassScanner.classes(cl, join(PKG_PREFIXES, AD_SDK_PREFIXES), 8000);
        for (Class<?> cls : all) {
            String cn = safeLower(cls.getName());
            boolean adClass = cn.contains("ad") || cn.contains("splash")
                    || cn.contains("banner") || cn.contains("reward")
                    || cn.contains("interstitial") || cn.contains("openad");
            Method[] ms;
            try { ms = cls.getDeclaredMethods(); } catch (Throwable t) { continue; }
            for (Method m : ms) {
                if (Reflect.isAbstract(m)) continue;
                String nm = safeLower(m.getName());
                boolean adMethod = nm.contains("ad") || nm.contains("splash")
                        || nm.contains("banner") || nm.contains("reward")
                        || nm.contains("interstitial");
                if (!adClass && !adMethod) continue;
                try {
                    if (Reflect.returnsBoolean(m) && Reflect.isNoArg(m)) {
                        if (nm.startsWith("show") || nm.startsWith("should")
                                || nm.startsWith("can") || nm.startsWith("is")
                                || nm.startsWith("has") || nm.startsWith("need")
                                || nm.contains("show")) {
                            n += replace(m, Boolean.FALSE, c);
                        }
                    } else if (Reflect.returnsVoid(m)) {
                        if (nm.startsWith("show") || nm.startsWith("load")
                                || nm.startsWith("display") || nm.startsWith("present")
                                || nm.startsWith("init") || nm.startsWith("start")
                                || nm.startsWith("play") || nm.startsWith("request")
                                || nm.startsWith("fetch")) {
                            n += replace(m, null, c);
                        }
                    } else if (Reflect.returnsString(m) && Reflect.isNoArg(m)) {
                        if (nm.contains("ad") && (nm.contains("url") || nm.contains("id")
                                || nm.contains("unit") || nm.contains("key"))) {
                            n += replace(m, "", c);
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ F16

    /** F16 - analytics / tracker disabler (perf + privacy, never breaks app).
     *  v3.1.0: scans target package AND third-party tracker SDK packages,
     *  plus a method-name heuristic for track/report verbs anywhere scanned. */
    public static int trackerBlock(ClassLoader cl, HookConfig cfg, Counter c) {
        if (!cfg.trackerBlock) return 0;
        int n = 0;
        List<Class<?>> all = ClassScanner.classes(cl, join(PKG_PREFIXES, TRACKER_SDK_PREFIXES), 8000);
        for (Class<?> cls : all) {
            String cn = safeLower(cls.getName());
            boolean tr = cn.contains("umeng") || cn.contains("analytics")
                    || cn.contains("tracker") || cn.contains("appsflyer")
                    || cn.contains("bugly") || cn.contains("flurry")
                    || cn.contains("adjust") || cn.contains("firebase")
                    || cn.contains("crashreport") || cn.contains("monitor")
                    || cn.contains("sensorsdata") || cn.contains("growingio")
                    || cn.contains("talkingdata") || cn.contains("metrica")
                    || cn.contains("amplitude") || cn.contains("mixpanel")
                    || cn.contains("branch") || cn.contains("onesignal")
                    || cn.contains("igexin") || cn.contains("sentry")
                    || cn.contains("mipush") || cn.contains("push");
            if (!tr) continue;
            Method[] ms;
            try { ms = cls.getDeclaredMethods(); } catch (Throwable t) { continue; }
            for (Method m : ms) {
                if (Reflect.isAbstract(m)) continue;
                try {
                    if (Reflect.returnsVoid(m)) {
                        String nm = safeLower(m.getName());
                        if (nm.startsWith("track") || nm.startsWith("report")
                                || nm.startsWith("log") || nm.startsWith("send")
                                || nm.startsWith("record") || nm.startsWith("init")
                                || nm.startsWith("on")) {
                            n += replace(m, null, c);
                        }
                    } else if (Reflect.returnsBoolean(m) && Reflect.isNoArg(m)) {
                        n += replace(m, Boolean.FALSE, c);
                    }
                } catch (Throwable ignored) {}
            }
        }
        // v3.1.0: verb heuristic across scanned packages (catches trackers
        // living inside the target package under neutral names).
        for (Class<?> cls : all) {
            Method[] ms;
            try { ms = cls.getDeclaredMethods(); } catch (Throwable t) { continue; }
            for (Method m : ms) {
                if (Reflect.isAbstract(m) || !Reflect.returnsVoid(m)) continue;
                String nm = safeLower(m.getName());
                if (nm.startsWith("trackevent") || nm.startsWith("logevent")
                        || nm.startsWith("sendevent") || nm.startsWith("recordevent")
                        || nm.startsWith("reportevent")) {
                    try { n += replace(m, null, c); } catch (Throwable ignored) {}
                }
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ F17

    /** F17 - forced-update / notice dialog suppressor. */
    public static int updateDialogBlock(ClassLoader cl, HookConfig cfg, Counter c) {
        if (!cfg.updateDialogBlock) return 0;
        int n = 0;
        List<Class<?>> all = ClassScanner.classes(cl, PKG_PREFIXES, 8000);
        for (Class<?> cls : all) {
            Method[] ms;
            try { ms = cls.getDeclaredMethods(); } catch (Throwable t) { continue; }
            for (Method m : ms) {
                if (Reflect.isAbstract(m)) continue;
                String nm = safeLower(m.getName());
                boolean dlg = (nm.contains("update") || nm.contains("upgrade")
                        || nm.contains("notice") || nm.contains("announce"))
                        && (nm.startsWith("show") || nm.startsWith("display")
                        || nm.startsWith("present") || nm.startsWith("popup")
                        || nm.startsWith("alert") || nm.contains("dialog"));
                if (!dlg) continue;
                try {
                    if (Reflect.returnsVoid(m) || Reflect.returnsBoolean(m)) {
                        n += replace(m, Reflect.returnsBoolean(m) ? (Object) Boolean.FALSE : null, c);
                    } else {
                        n += replace(m, null, c);
                    }
                } catch (Throwable ignored) {}
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ F18

    /** F18 - hides in-app VIP purchase UI (BuyVip / VipCard / pay dialogs).
     *  Only touches classes whose name mentions vip+buy/card/pay/dialog, and
     *  only show/display/present (void->no-op) or should/show/is/has/need
     *  (boolean->false) gates. Never touches generic View methods. */
    public static int hideVipUi(ClassLoader cl, HookConfig cfg, Counter c) {
        if (!cfg.hideVipUi) return 0;
        int n = 0;
        List<Class<?>> all = ClassScanner.classes(cl, PKG_PREFIXES, 8000);
        for (Class<?> cls : all) {
            String cn = safeLower(cls.getName());
            boolean vipUi = cn.contains("buyvip") || cn.contains("vipcard")
                    || cn.contains("vippay") || cn.contains("payvip")
                    || cn.contains("vipdialog") || cn.contains("vippopup");
            if (!vipUi) continue;
            Method[] ms;
            try { ms = cls.getDeclaredMethods(); } catch (Throwable t) { continue; }
            for (Method m : ms) {
                if (Reflect.isAbstract(m)) continue;
                String nm = safeLower(m.getName());
                try {
                    if (Reflect.returnsVoid(m)) {
                        if (nm.startsWith("show") || nm.startsWith("display")
                                || nm.startsWith("present") || nm.startsWith("popup")
                                || nm.startsWith("open") || nm.startsWith("load")) {
                            n += replace(m, null, c);
                        }
                    } else if (Reflect.returnsBoolean(m) && Reflect.isNoArg(m)) {
                        if (nm.startsWith("should") || nm.startsWith("show")
                                || nm.startsWith("is") || nm.startsWith("has")
                                || nm.startsWith("need") || nm.contains("visible")) {
                            n += replace(m, Boolean.FALSE, c);
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ F19

    /** F19 - VipItem unlock hardening (com.novan.morpha.VipItem#isVipItem).
     *  Fast path on the known class, then a name-heuristic fallback.
     *  Boolean vip gates -> true, int level/coin -> 9999, long expiry -> far-future. */
    public static int vipItemUnlock(ClassLoader cl, HookConfig cfg, Counter c) {
        if (!cfg.vipItem) return 0;
        int n = 0;
        Class<?> cls = Reflect.firstClass(cl,
                "com.novan.morpha.VipItem", "com.novan.VipItem");
        if (cls == null) cls = ClassScanner.bySimpleName(cl, "VipItem", PKG_PREFIXES);
        if (cls != null) {
            c.ok("VipItem = " + cls.getName());
            try {
                for (Method m : cls.getDeclaredMethods()) {
                    if (Reflect.isAbstract(m) || !Reflect.isNoArg(m)) continue;
                    String nm = safeLower(m.getName());
                    try {
                        if (Reflect.returnsBoolean(m)) {
                            if (nm.contains("vip") || nm.contains("premium")
                                    || nm.contains("paid") || nm.contains("unlock")) {
                                n += replace(m, Boolean.TRUE, c);
                            }
                        } else if (Reflect.returnsInt(m)) {
                            if (nm.contains("vip") || nm.contains("level")
                                    || nm.contains("coin") || nm.contains("point")) {
                                n += replace(m, Integer.valueOf(9999), c);
                            }
                        } else if (Reflect.returnsLong(m)) {
                            if (nm.contains("expire") || nm.contains("vip")
                                    || nm.contains("coin")) {
                                n += replace(m, Long.valueOf(4102444800000L), c);
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            } catch (Throwable ignored) {}
        }
        // fallback: any vipitem-ish class the fast path missed
        for (Class<?> k : ClassScanner.classes(cl, PKG_PREFIXES, 8000)) {
            String cn = safeLower(k.getName());
            if (!cn.contains("vipitem")) continue;
            if (cls != null && k.getName().equals(cls.getName())) continue;
            try {
                for (Method m : k.getDeclaredMethods()) {
                    if (Reflect.isAbstract(m) || !Reflect.isNoArg(m)) continue;
                    if (!Reflect.returnsBoolean(m)) continue;
                    String nm = safeLower(m.getName());
                    if (nm.contains("vip") || nm.contains("premium") || nm.contains("unlock")) {
                        n += replace(m, Boolean.TRUE, c);
                    }
                }
            } catch (Throwable ignored) {}
        }
        return n;
    }

    // ------------------------------------------------------------------ F20

    /** F20 - license / store-redirect neutralizer (anti "mental ke Play Store").
     *  Licensed/paid gates -> TRUE (we hold a license); void store openers
     *  (openPlayStore/rateApp/goMarket...) -> no-op so the app can never
     *  bounce the user to the Play Store. Pairip flavor included. */
    public static int licenseBypass(ClassLoader cl, HookConfig cfg, Counter c) {
        if (!cfg.licenseBypass) return 0;
        int n = 0;
        List<Class<?>> all = ClassScanner.classes(cl, PKG_PREFIXES, 8000);
        for (Class<?> cls : all) {
            Method[] ms;
            try { ms = cls.getDeclaredMethods(); } catch (Throwable t) { continue; }
            for (Method m : ms) {
                if (Reflect.isAbstract(m)) continue;
                String nm = safeLower(m.getName());
                boolean licMethod = nm.contains("license") || nm.contains("licence")
                        || nm.contains("pairip");
                boolean storeOpener = nm.contains("openplaystore") || nm.contains("openmarket")
                        || nm.contains("gotomarket") || nm.contains("rateapp")
                        || nm.contains("openshop") || nm.contains("openstore");
                if (!licMethod && !storeOpener) continue;
                try {
                    if (storeOpener && Reflect.returnsVoid(m)) {
                        n += replace(m, null, c);
                    } else if (Reflect.returnsBoolean(m) && Reflect.isNoArg(m)) {
                        if (nm.contains("islicensed") || nm.contains("haslicense")
                                || nm.contains("isregistered") || nm.contains("isactivated")
                                || nm.contains("checklicense") || nm.contains("verifylicense")) {
                            n += replace(m, Boolean.TRUE, c);
                        } else if (nm.contains("unlicensed") || nm.contains("invalidlicense")) {
                            n += replace(m, Boolean.FALSE, c);
                        }
                    } else if (Reflect.returnsVoid(m) && licMethod) {
                        if (nm.startsWith("check") || nm.startsWith("verify")
                                || nm.startsWith("validate")) {
                            n += replace(m, null, c);
                        }
                    } else if (Reflect.returnsString(m) && Reflect.isNoArg(m) && licMethod) {
                        if (nm.contains("url") || nm.contains("key") || nm.contains("id")) {
                            n += replace(m, "", c);
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ F12

    /** F12 - installs a last-resort guard so a hook failure can never kill the app. */
    public static int crashGuard() {
        try {
            final Thread.UncaughtExceptionHandler prev =
                    Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread t, Throwable e) {
                    try {
                        if (e != null && e.getStackTrace() != null) {
                            for (StackTraceElement st : e.getStackTrace()) {
                                if (st != null && st.getClassName() != null
                                        && st.getClassName().startsWith("com.loktv.hook")) {
                                    Log.e("crash-guard swallowed: " + e.getClass().getName(), e);
                                    return;
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                    try {
                        if (prev != null && prev != this) prev.uncaughtException(t, e);
                    } catch (Throwable ignored) {}
                }
            });
            return 1;
        } catch (Throwable t) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ helpers

    /**
     * v3.1.0 smart gate: boolean method -> replace with TRUE;
     * void method (v1-style installer) -> INVOKE it now (idempotent via
     * HookRegistry); anything else -> skip with a log.
     */
    private static int gateTrue(Class<?> cls, String method, Counter c) {
        Method m = Reflect.methodNoArg(cls, method);
        if (m == null) { c.fail(cls.getSimpleName() + "#" + method + " not found", null); return 0; }
        if (Reflect.returnsBoolean(m)) return replace(m, Boolean.TRUE, c);
        if (Reflect.returnsVoid(m)) return invokeInstaller(m, c);
        c.fail(cls.getSimpleName() + "#" + method + " unexpected return type", null);
        return 0;
    }

    /** v3.1.0 smart no-op: void installer -> INVOKE; boolean -> FALSE; else replace null. */
    private static int gateNoop(Class<?> cls, String method, Counter c) {
        Method m = Reflect.methodNoArg(cls, method);
        if (m == null) { c.fail(cls.getSimpleName() + "#" + method + " not found", null); return 0; }
        if (Reflect.returnsVoid(m)) return invokeInstaller(m, c);
        if (Reflect.returnsBoolean(m)) return replace(m, Boolean.FALSE, c);
        return replace(m, null, c);
    }

    /**
     * Invokes a v1-style void installer (it self-installs the real hooks).
     * Idempotent across Engine passes via HookRegistry. Never throws.
     */
    private static int invokeInstaller(final Method m, Counter c) {
        try {
            String key;
            try {
                key = m.getDeclaringClass().getName() + "#" + m.getName();
            } catch (Throwable t) {
                return 0;
            }
            if (!HookRegistry.markInvokedIfNew(key)) return 0;
            m.invoke(null);
            c.ok("invoked installer " + m.getDeclaringClass().getSimpleName() + "#" + m.getName());
            return 1;
        } catch (Throwable t) {
            c.fail("installer failed " + m.getName(), t);
            return 0;
        }
    }

    /**
     * F21 - invokes every known v1-style void installer found in the dex.
     * Runs FIRST so the original self-installed hooks exist before our
     * Xposed hooks complement them. Missing classes fail silently.
     */
    public static int legacyInstallers(ClassLoader cl, HookConfig cfg, Counter c) {
        if (!cfg.legacyInvoke) return 0;
        int n = 0;
        n += invokeClassInstallers(cl,
                new String[]{"com.novan.morpha.UserStatus", "com.novan.UserStatus"},
                "UserStatus",
                new String[]{"isVip", "isSkipUpdate", "isValidCollection",
                        "isVideoDataEnabled", "DisableSomePopup"}, c);
        n += invokeClassInstallers(cl,
                new String[]{"com.novan.morpha.FloatingView", "com.novan.FloatingView"},
                "FloatingView",
                new String[]{"isDisable"}, c);
        n += invokeClassInstallers(cl,
                new String[]{"com.novan.morpha.VipItem", "com.novan.VipItem"},
                "VipItem",
                new String[]{"isVipItem"}, c);
        n += invokeClassInstallers(cl,
                new String[]{"com.novan.morpha.HideBuyVip", "com.novan.HideBuyVip"},
                "HideBuyVip",
                new String[]{"init"}, c);
        n += invokeClassInstallers(cl,
                new String[]{"com.novan.morpha.HideVipCard", "com.novan.HideVipCard"},
                "HideVipCard",
                new String[]{"init"}, c);
        n += invokeClassInstallers(cl,
                new String[]{"com.novan.morpha.ReplaceVipString", "com.novan.ReplaceVipString"},
                "ReplaceVipString",
                new String[]{"init", "init2"}, c);
        return n;
    }

    private static int invokeClassInstallers(ClassLoader cl, String[] fastPath,
            String simpleName, String[] methods, Counter c) {
        int n = 0;
        try {
            Class<?> cls = Reflect.firstClass(cl, fastPath);
            if (cls == null) cls = ClassScanner.bySimpleName(cl, simpleName, PKG_PREFIXES);
            if (cls == null) return 0;
            for (String name : methods) {
                try {
                    Method m = Reflect.methodNoArg(cls, name);
                    if (m == null || !Reflect.returnsVoid(m)) continue;
                    n += invokeInstaller(m, c);
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return n;
    }

    private static int forceFalseAnyName(Class<?> cls, String[] names, Counter c) {
        int n = 0;
        for (String name : names) {
            Method m = Reflect.methodNoArg(cls, name);
            if (m == null) continue;
            if (Reflect.returnsBoolean(m)) n += replace(m, Boolean.FALSE, c);
            else n += replace(m, null, c);
        }
        return n;
    }

    private static int forceFalseAnyName(Class<?> cls, Counter c, String... names) {
        return forceFalseAnyName(cls, names, c);
    }

    private static int noop(Class<?> cls, String method, Counter c) {
        Method m = Reflect.methodNoArg(cls, method);
        if (m == null) { c.fail(cls.getSimpleName() + "#" + method + " not found", null); return 0; }
        return replace(m, null, c);
    }

    private static int noopAnyName(Class<?> cls, Counter c, String... names) {
        int n = 0;
        for (String name : names) {
            Method m = Reflect.methodNoArg(cls, name);
            if (m != null) n += replace(m, null, c);
        }
        return n;
    }

    private static int forceIntIfPresent(Class<?> cls, Counter c, String[] names, int value) {
        int n = 0;
        try {
            for (Method m : cls.getDeclaredMethods()) {
                if (!Reflect.isNoArg(m) || Reflect.isAbstract(m)) continue;
                if (!Reflect.returnsInt(m)) continue;
                String nm = safeLower(m.getName());
                for (String k : names) {
                    if (nm.equals(safeLower(k))) { n += replace(m, Integer.valueOf(value), c); break; }
                }
            }
        } catch (Throwable ignored) {}
        return n;
    }

    private static int forceLongIfPresent(Class<?> cls, Counter c, String[] names, long value) {
        int n = 0;
        try {
            for (Method m : cls.getDeclaredMethods()) {
                if (!Reflect.isNoArg(m) || Reflect.isAbstract(m)) continue;
                if (!Reflect.returnsLong(m)) continue;
                String nm = safeLower(m.getName());
                for (String k : names) {
                    if (nm.equals(safeLower(k))) { n += replace(m, Long.valueOf(value), c); break; }
                }
            }
        } catch (Throwable ignored) {}
        return n;
    }

    private static int replace(final Method m, final Object value, Counter c) {
        try {
            if (HookRegistry.alreadyHooked(m)) return 0;
            XposedBridge.hookMethod(m, new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return value;
                }
            });
            HookRegistry.markHooked(m);
            c.ok("hooked " + m.getDeclaringClass().getSimpleName() + "#" + m.getName() + " -> " + value);
            return 1;
        } catch (Throwable t) {
            c.fail("hook failed " + m.getName(), t);
            return 0;
        }
    }

    private static boolean contains(String[] keys, String name) {
        if (name == null) return false;
        for (String k : keys) {
            if (k != null && name.contains(k)) return true;
        }
        return false;
    }

    /** Concatenates two prefix arrays (null-safe). */
    private static String[] join(String[] a, String[] b) {
        int na = a == null ? 0 : a.length;
        int nb = b == null ? 0 : b.length;
        String[] out = new String[na + nb];
        for (int i = 0; i < na; i++) out[i] = a[i];
        for (int i = 0; i < nb; i++) out[na + i] = b[i];
        return out;
    }

    private static String safeLower(String s) {
        try { return s == null ? "" : s.toLowerCase(java.util.Locale.US); }
        catch (Throwable t) { return s == null ? "" : s.toLowerCase(); }
    }

    private Features() {}
}
