package com.kakuaudit.observe.core;

import static org.junit.Assert.*;
import org.junit.Test;

public class SafeGuardTest {
    @Test public void swallowsAndCounts() {
        SafeGuard.resetForTests();
        boolean ok = SafeGuard.runSafe("t1", null, () -> { throw new RuntimeException("x"); });
        assertFalse(ok);
        assertEquals(1, SafeGuard.failureCount("t1"));
        // App continues: a good run still works.
        assertTrue(SafeGuard.runSafe("t1", null, () -> {}));
    }

    @Test public void killSwitchAfterThreshold() {
        SafeGuard.resetForTests();
        for (int i = 0; i < 25; i++) {
            SafeGuard.runSafe("noisy", null, () -> { throw new RuntimeException("boom"); });
        }
        assertTrue(SafeGuard.isKilled("noisy"));
        // Killed tags are skipped without running.
        final boolean[] ran = {false};
        assertFalse(SafeGuard.runSafe("noisy", null, () -> { ran[0] = true; }));
        assertFalse(ran[0]);
        SafeGuard.resetForTests();
    }

    @Test public void threadNeverThrows() throws Exception {
        SafeGuard.resetForTests();
        Thread t = SafeGuard.thread("ut", () -> { throw new RuntimeException("bg"); }, null);
        t.start();
        t.join(2000);
        assertEquals(1, SafeGuard.failureCount("thread:ut"));
    }
}
