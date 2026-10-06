package com.kakuaudit.observe.storage;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import com.kakuaudit.observe.core.PathSafety;
import com.kakuaudit.observe.core.VersionCompat;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Spec §7: public Download (full pkg/ver/session path) -&gt; app-private
 * staging + MediaStore export so files are visible in Download on Q+.
 * Rootless: no shell, no /data access. Records requestedRoot vs actualRoot.
 * Writability is proven by tmp-file round-trip, never File.canWrite() alone.
 *
 * v3.1.2 fix: tryLegacy previously returned &lt;Download&gt;/KakuAudit without
 * the session subtree (SessionManager then built the session in the wrong
 * parent), and MEDIASTORE resolution had sessionDir=null so files landed
 * invisibly in app-private with no log telling the user where.
 */
public final class StorageRouter {

    public enum Mechanism {
        PUBLIC_DOWNLOAD, APP_PRIVATE_STAGED, APP_PRIVATE_UNWRITABLE
    }

    public static final class Resolution {
        public final Mechanism mechanism;
        public final String requestedRoot = "/storage/emulated/0/Download/KakuAudit";
        /** Absolute dir where session files were (or will be) written. */
        public final String actualRoot;
        public final File sessionDir;
        /** App-private staging dir (always set; used as fallback + export source). */
        public final File stagingDir;
        /** Relative MediaStore path for export, e.g. Download/KakuAudit/pkg/ver/sess. */
        public final String exportRelPath;
        public final boolean writable;
        public final int apiLevel;
        public final String diagnostic;
        public Resolution(Mechanism m, String actual, File dir, File staging,
                          String exportRel, boolean w, String d) {
            mechanism = m; actualRoot = actual; sessionDir = dir;
            stagingDir = staging; exportRelPath = exportRel; writable = w;
            apiLevel = VersionCompat.apiLevel(); diagnostic = d;
        }
    }

    private StorageRouter() {}

    /**
     * Resolve the session dir: public Download full path when really writable,
     * else app-private staging (MediaStore export happens after writeAll).
     */
    public static Resolution resolve(Context ctx, String pkg, String version,
                                     String sessionId) {
        String p = PathSafety.normalizeSegment(pkg, "unknown");
        String v = PathSafety.normalizeSegment(version == null ? "0" : version, "0");
        String s = PathSafety.normalizeSegment(sessionId, "session");

        File staging = new File(ctx.getFilesDir(), "KakuAudit/" + p + "/" + v + "/" + s);
        String exportRel = "Download/KakuAudit/" + p + "/" + v + "/" + s;

        File pub = publicSessionDir(p, v, s);
        if (pub != null && proveWritable(pub)) {
            return new Resolution(Mechanism.PUBLIC_DOWNLOAD,
                    pub.getAbsolutePath(), pub, staging, exportRel, true,
                    "public-download-writable");
        }
        boolean ok = proveWritable(staging);
        return new Resolution(
                ok ? Mechanism.APP_PRIVATE_STAGED : Mechanism.APP_PRIVATE_UNWRITABLE,
                staging.getAbsolutePath(), ok ? staging : null, staging, exportRel, ok,
                ok ? "staged-app-private-export-pending" : "app-private-unwritable");
    }

    /** Full public path: &lt;Download&gt;/KakuAudit/pkg/ver/session. Null when unavailable. */
    private static File publicSessionDir(String p, String v, String s) {
        try {
            File root = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
            if (root == null) return null;
            return new File(root, "KakuAudit/" + p + "/" + v + "/" + s);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Copy session files into public Download via MediaStore (Q+) so they are
     * visible even when staging was app-private. Returns exported file count.
     * Never throws.
     */
    public static int exportToDownloads(Context ctx, Resolution res) {
        if (ctx == null || res == null || res.sessionDir == null) return 0;
        if (!VersionCompat.atLeast(29)) return 0;
        // Already public: nothing to export.
        if (res.mechanism == Mechanism.PUBLIC_DOWNLOAD) return 0;
        File dir = res.sessionDir;
        File[] files;
        try {
            files = dir.listFiles();
        } catch (Throwable t) {
            return 0;
        }
        if (files == null || files.length == 0) return 0;
        int exported = 0;
        for (File f : files) {
            try {
                if (!f.isFile() || f.getName().startsWith(".")
                        || f.getName().endsWith(".tmp")) continue;
                if (insertOne(ctx, res.exportRelPath, f)) exported++;
            } catch (Throwable ignore) { /* per-file isolation */ }
        }
        return exported;
    }

    private static boolean insertOne(Context ctx, String relPath, File src) {
        ContentResolver cr;
        try {
            cr = ctx.getContentResolver();
        } catch (Throwable t) {
            return false;
        }
        String mime = src.getName().endsWith(".html") ? "text/html"
                : src.getName().endsWith(".json") || src.getName().endsWith(".jsonl")
                ? "application/json" : "application/octet-stream";
        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Downloads.DISPLAY_NAME, src.getName());
        cv.put(MediaStore.Downloads.MIME_TYPE, mime);
        cv.put(MediaStore.Downloads.RELATIVE_PATH, relPath);
        Uri uri;
        try {
            uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv);
        } catch (Throwable t) {
            return false;
        }
        if (uri == null) return false;
        try (OutputStream os = cr.openOutputStream(uri);
             InputStream in = new FileInputStream(src)) {
            if (os == null) {
                try { cr.delete(uri, null, null); } catch (Throwable ignore) {}
                return false;
            }
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
            os.flush();
            return true;
        } catch (Throwable t) {
            try { cr.delete(uri, null, null); } catch (Throwable ignore) {}
            return false;
        }
    }

    /** Real round-trip: mkdirs -&gt; write unique tmp -&gt; read -&gt; delete. */
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

    /** API for SAF user-picked tree (persisted URI). Kept for future use. */
    public static boolean persistSafPermission(Context ctx, Uri treeUri) {
        try {
            int flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
            ctx.getContentResolver().takePersistableUriPermission(treeUri, flags);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
