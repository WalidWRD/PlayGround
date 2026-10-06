package com.kakuaudit.observe.storage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Spec §14.1: tmp write -> flush/close -> validate -> atomic rename.
 * Never mark complete before validation + commit.
 */
public final class AtomicFile {
    private AtomicFile() {}

    public interface Validator {
        boolean valid(byte[] bytes);
    }

    public static boolean write(File target, byte[] bytes, Validator v) {
        if (target == null || bytes == null) return false;
        if (v != null && !v.valid(bytes)) return false;
        File dir = target.getParentFile();
        if (dir != null && !dir.exists()) dir.mkdirs();
        File tmp = new File(target.getParent(),
                target.getName() + ".tmp-" + System.nanoTime());
        try (OutputStream os = new FileOutputStream(tmp)) {
            os.write(bytes);
            os.flush();
        } catch (Throwable t) {
            try { tmp.delete(); } catch (Throwable ignore) {}
            return false;
        }
        // Validate post-write read-back for JSON (cheap, synchronous).
        if (v != null) {
            try {
                byte[] back = java.nio.file.Files.readAllBytes(tmp.toPath());
                if (!v.valid(back)) { tmp.delete(); return false; }
            } catch (Throwable t) { tmp.delete(); return false; }
        }
        try {
            if (target.exists() && !target.delete()) { tmp.delete(); return false; }
            boolean renamed = tmp.renameTo(target);
            if (!renamed) {
                // Cross-volume fallback: copy then delete.
                try (OutputStream os = new FileOutputStream(target)) {
                    os.write(java.nio.file.Files.readAllBytes(tmp.toPath()));
                }
                tmp.delete();
            }
            return target.exists();
        } catch (Throwable t) {
            try { tmp.delete(); } catch (Throwable ignore) {}
            return false;
        }
    }

    public static boolean writeUtf8(File target, String content, Validator v) {
        return write(target, content.getBytes(StandardCharsets.UTF_8), v);
    }
}
