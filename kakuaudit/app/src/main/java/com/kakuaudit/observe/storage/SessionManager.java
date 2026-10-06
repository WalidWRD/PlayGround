package com.kakuaudit.observe.storage;

import com.kakuaudit.observe.core.KakuClock;
import com.kakuaudit.observe.core.PathSafety;

import java.io.File;
import java.util.UUID;

/**
 * Spec §8-9: unique sessionId, per-session lock, atomic index update.
 * Never overwrite previous sessions; collisions create a new session.
 */
public final class SessionManager {
    public final String packageName;
    public final String version;
    public final String sessionId;
    public final String startedAt;
    public final File sessionDir;
    public final StorageRouter.Resolution storage;
    private final File lockFile;

    public SessionManager(android.content.Context ctx, String pkg, String ver,
                          StorageRouter.Resolution storage) {
        this.packageName = PathSafety.normalizeSegment(pkg, "unknown");
        this.version = PathSafety.normalizeSegment(ver == null ? "0" : ver, "0");
        this.sessionId = PathSafety.newSessionId();
        this.startedAt = KakuClock.utcNowIso();
        this.storage = storage;
        File base = storage.sessionDir != null ? storage.sessionDir.getParentFile()
                : new File(ctx.getFilesDir(), "KakuAudit/" + packageName + "/" + version);
        this.sessionDir = new File(base, sessionId);
        this.lockFile = new File(sessionDir, ".lock");
    }

    /** Acquire per-session lock (pid + time + lease). Returns false on conflict. */
    public boolean acquire() {
        try {
            if (!sessionDir.exists() && !sessionDir.mkdirs()) return false;
            if (lockFile.exists()) {
                // Stale-lock detection: lease 10 min.
                long age = System.currentTimeMillis() - lockFile.lastModified();
                if (age < 10 * 60 * 1000L) return false; // live lock -> caller must new session
                // stale: keep diagnostic, reclaim.
            }
            AtomicFile.writeUtf8(lockFile,
                    "{\"pid\":" + android.os.Process.myPid()
                    + ",\"startedAt\":\"" + startedAt + "\"}", null);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public void release() {
        try { lockFile.delete(); } catch (Throwable ignore) {}
    }

    public File file(String name) {
        return new File(sessionDir, name);
    }

    public static String newIdOnCollision() {
        return PathSafety.newSessionId() + "-" + UUID.randomUUID().toString().substring(0, 4);
    }
}
