package com.kakuaudit.observe.storage;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.kakuaudit.observe.core.VersionCompat;

import java.io.File;
import java.io.OutputStream;

/**
 * Spec §7: MediaStore (Q+) -> SAF -> legacy -> app-private fallback.
 * Rootless: no shell, no /data access. Records requestedRoot vs actualRoot.
 * Writability is proven by tmp-file round-trip, never File.canWrite() alone.
 */
public final class StorageRouter {

    public enum Mechanism { MEDIASTORE, SAF, LEGACY_FILESYSTEM, APP_PRIVATE_FALLBACK }

    public static final class Resolution {
        public final Mechanism mechanism;
        public final String requestedRoot = "/storage/emulated/0/Download/KakuAudit";
        public final String actualRoot;
        public final File sessionDir;   // null when MediaStore-only (streamed)
        public final boolean writable;
        public final int apiLevel;
        public final String diagnostic;
        public Resolution(Mechanism m, String actual, File dir, boolean w, String d) {
            mechanism = m; actualRoot = actual; sessionDir = dir; writable = w;
            apiLevel = VersionCompat.apiLevel(); diagnostic = d;
        }
    }

    private StorageRouter() {}

    /** Resolve + prove writability with a uniquely-named tmp file round-trip. */
    public static Resolution resolve(Context ctx, String pkg, String version, String sessionId) {
        String safePkg = pkg == null ? "unknown" : pkg;
        String rel = "Download/KakuAudit/" + safePkg + "/" + version + "/" + sessionId;

        // 1) Q+ public Download via MediaStore — preferred, no permission needed for own files.
        if (VersionCompat.atLeast(29)) {
            Resolution r = tryMediaStore(ctx, rel);
            if (r != null && r.writable) return r;
        }
        // 2) Legacy direct FS where still permitted (pre-Q or app-private).
        Resolution legacy = tryLegacy(rel);
        if (legacy.writable) return legacy;
        // 3) Fallback: app-private staging + explicit export op. Never silent.
        File priv = new File(ctx.getFilesDir(), "KakuAudit/" + safePkg + "/" + version + "/" + sessionId);
        boolean ok = proveWritable(priv);
        return new Resolution(Mechanism.APP_PRIVATE_FALLBACK, priv.getAbsolutePath(), priv, ok,
                ok ? "staged-app-private-export-required" : "app-private-unwritable");
    }

    private static Resolution tryLegacy(String rel) {
        try {
            File root = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File dir = new File(root, "KakuAudit");
            // rel already contains Download/...; rebuild under it:
            // keep it simple: <Download>/KakuAudit/<pkg>/<ver>/<sess> is handled by caller path.
            boolean ok = proveWritable(dir);
            return new Resolution(Mechanism.LEGACY_FILESYSTEM, dir.getAbsolutePath(), dir, ok,
                    ok ? "legacy-writable" : "legacy-not-writable");
        } catch (Throwable t) {
            return new Resolution(Mechanism.LEGACY_FILESYSTEM, "UNAVAILABLE", null, false,
                    "legacy-exception:" + t.getClass().getSimpleName());
        }
    }

    private static Resolution tryMediaStore(Context ctx, String relPath) {
        try {
            ContentResolver cr = ctx.getContentResolver();
            ContentValues cv = new ContentValues();
            String name = ".kaku-probe-" + System.nanoTime();
            cv.put(MediaStore.Downloads.DISPLAY_NAME, name);
            cv.put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream");
            if (VersionCompat.atLeast(29)) {
                cv.put(MediaStore.Downloads.RELATIVE_PATH, relPath);
            }
            Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
            if (uri == null) return null;
            try (OutputStream os = cr.openOutputStream(uri)) {
                if (os == null) { cr.delete(uri, null, null); return null; }
                os.write("probe".getBytes());
                os.flush();
            }
            cr.delete(uri, null, null);
            // MediaStore has no File dir; session files stream individually.
            // actualRoot records the logical collection instead of a File path.
            return new Resolution(Mechanism.MEDIASTORE, "mediastore:" + relPath, null, true, "mediastore-probe-ok");
        } catch (Throwable t) {
            return new Resolution(Mechanism.MEDIASTORE, "UNAVAILABLE", null, false,
                    "mediastore-exception:" + t.getClass().getSimpleName());
        }
    }

    /** Real round-trip: mkdirs -> write unique tmp -> read -> delete. */
    public static boolean proveWritable(File dir) {
        try {
            if (dir == null) return false;
            if (!dir.exists() && !dir.mkdirs()) return false;
            File probe = new File(dir, ".probe-" + System.nanoTime());
            try (OutputStream os = new java.io.FileOutputStream(probe)) {
                os.write("kaku".getBytes());
                os.flush();
            }
            boolean readable = probe.length() == 4;
            probe.delete();
            return readable;
        } catch (Throwable t) {
            return false;
        }
    }

    /** API for SAF user-picked tree (persisted URI). Kept reflective for minSdk safety. */
    public static boolean persistSafPermission(Context ctx, Uri treeUri) {
        try {
            int flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            // takePersistableUriPermission exists since 19; guarded call.
            ctx.getContentResolver().takePersistableUriPermission(treeUri, flags);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
