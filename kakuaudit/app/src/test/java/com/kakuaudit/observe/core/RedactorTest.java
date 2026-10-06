package com.kakuaudit.observe.core;

import static org.junit.Assert.*;
import org.junit.Test;

public class RedactorTest {
    @Test public void deniesSecrets() {
        assertTrue(Redactor.mustRedact("authorization"));
        assertTrue(Redactor.mustRedact("purchase_token"));
        assertTrue(Redactor.mustRedact("X-Api-Key"));
        assertTrue(Redactor.mustRedact("cookie"));
        assertFalse(Redactor.mustRedact("feature_state"));
    }
    @Test public void sanitizesUrl() {
        String u = Redactor.sanitizeUrl("https://x.test/pay?token=abc&order=1");
        assertTrue(u.contains("token=REDACTED"));
        assertFalse(u.contains("abc"));
    }
    @Test public void confidenceScale() {
        assertEquals(Confidence.Level.WEAK, Confidence.levelFor(0.1));
        assertEquals(Confidence.Level.STRONG, Confidence.levelFor(0.86));
        assertEquals(0.24, Confidence.capForStaticHint(0.9), 1e-9);
    }
    @Test public void pathSafety() {
        assertEquals("unknown", PathSafety.normalizeSegment("../evil", "unknown"));
        assertEquals("unknown", PathSafety.normalizeSegment("a/b", "unknown"));
        assertTrue(PathSafety.isPackageSafe("com.example.app"));
    }
    @Test public void eventBusBounds() {
        java.util.List<String> out = new java.util.ArrayList<>();
        EventBus b = new EventBus(4, 1.0, out::add);
        for (int i = 0; i < 100; i++) b.publish("{\"schemaVersion\":\"3.0\"}", i);
        // bounded: accepted + dropped == attempts (async drain may still be pending)
        assertTrue(b.accepted() + b.dropped() == 100);
        b.close();
    }
}
