package com.kakuaudit.observe;

/**
 * LSPosed / LSPatch entry. Listed in assets/xposed_init.
 * Delegates to {@link KakuAuditHook} without importing the Xposed API so the
 * bytecode loads even where the API class is absent (pure reflection).
 * Logs the embedded module description to the Xposed boot log on startup.
 */
public final class KakuAuditXposed {
    public KakuAuditXposed() {}

    // Called by LSPosed once at startup (IXposedHookZygoteInit#initZygote).
    public void initZygote(Object startupParam) {
        ModuleDescription.logToXposed("initZygote");
    }

    // Signature matches IXposedHookLoadPackage#handleLoadPackage(LoadPackageParam).
    public void handleLoadPackage(Object lpparam) {
        KakuAuditHook.handleLoadPackage(lpparam);
    }
}
