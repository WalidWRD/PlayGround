package com.kakuaudit.observe.report;

import java.util.ArrayList;
import java.util.List;

/**
 * Spec §16: lightweight structural validation (no external deps).
 * Full JSON-Schema files live in assets/schemas/ for host-side validation;
 * on-device we enforce required fields + size bounds before COMPLETE.
 */
public final class JsonValidator {
    private JsonValidator() {}

    public static final class Result {
        public boolean ok = true;
        public final List<String> errors = new ArrayList<>();
        void fail(String e) { ok = false; errors.add(e); }
    }

    public static Result validateLine(String jsonLine) {
        Result r = new Result();
        if (jsonLine == null || jsonLine.isEmpty()) { r.fail("empty-line"); return r; }
        String t = jsonLine.trim();
        if (!(t.startsWith("{") && t.endsWith("}"))) { r.fail("not-an-object"); return r; }
        if (!t.contains("\"schemaVersion\"")) r.fail("missing-schemaVersion");
        if (!t.contains("\"eventId\"") && !t.contains("\"type\"") && !t.contains("\"eventType\""))
            r.fail("missing-identity");
        if (jsonLine.length() > 65536) r.fail("exceeds-maxSingleEventBytes");
        return r;
    }

    public static boolean looksLikeJson(String s) {
        if (s == null) return false;
        String t = s.trim();
        return (t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"));
    }
}
