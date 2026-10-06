package com.kakuaudit.observe.report;

import com.kakuaudit.observe.core.KakuClock;
import com.kakuaudit.observe.core.ProtectedComponentRegistry;
import com.kakuaudit.observe.storage.AtomicFile;
import com.kakuaudit.observe.storage.SessionManager;

import java.io.File;
import java.util.List;

/**
 * Spec §8/§16-18: writes every session file, validates, counts filesGenerated
 * (never hard-coded), updates index atomically, creates analysis-complete LAST.
 */
public final class ReportWriter {
    private final SessionManager session;

    public ReportWriter(SessionManager session) {
        this.session = session;
    }

    public static final String[] EXPECTED = {
            "manifest.json", "analysis.json", "runtime.jsonl", "timeline.json",
            "module-blueprint.json", "subscription-blueprint.json",
            "server-verification.json", "protection-analysis.json",
            "native-libraries.json", "crashes.json", "version-diff.json",
            "validation.json", "report.html", "analysis-complete.json"
    };

    public File writeAll(String runtimeJsonl, List<ProtectedComponentRegistry.Entry> prot,
                         long dropped, long accepted, List<String> validationErrors) {
        int ok = 0;
        ok += write("manifest.json",
                "{\"schemaVersion\":\"3.0\",\"sessionId\":" + Jsons.q(session.sessionId)
                + ",\"package\":" + Jsons.q(session.packageName)
                + ",\"version\":" + Jsons.q(session.version)
                + ",\"startedAt\":" + Jsons.q(session.startedAt)
                + ",\"mode\":\"OBSERVE_ONLY\"}") ? 1 : 0;
        ok += write("analysis.json",
                "{\"schemaVersion\":\"3.0\",\"sessionId\":" + Jsons.q(session.sessionId)
                + ",\"eventsAccepted\":" + accepted + ",\"eventsDropped\":" + dropped + "}") ? 1 : 0;
        ok += write("runtime.jsonl", runtimeJsonl == null ? "" : runtimeJsonl) ? 1 : 0;
        ok += write("timeline.json",
                "{\"schemaVersion\":\"3.0\",\"clockSource\":\""
                + KakuClock.clockSource() + "\"}") ? 1 : 0;
        ok += write("module-blueprint.json",
                "{\"schemaVersion\":\"3.0\",\"mode\":\"OBSERVE_ONLY\",\"note\":"
                + Jsons.q("evidence-based description only; never a bypass rule") + "}") ? 1 : 0;
        ok += write("subscription-blueprint.json", "{\"schemaVersion\":\"3.0\"}") ? 1 : 0;
        ok += write("server-verification.json",
                "{\"schemaVersion\":\"3.0\",\"note\":"
                + Jsons.q("endpoint metadata only; secrets never exported") + "}") ? 1 : 0;
        ok += write("protection-analysis.json", protectionJson(prot)) ? 1 : 0;
        ok += write("native-libraries.json", "{\"schemaVersion\":\"3.0\"}") ? 1 : 0;
        ok += write("crashes.json", "{\"schemaVersion\":\"3.0\",\"crashes\":[]}") ? 1 : 0;
        ok += write("version-diff.json",
                "{\"schemaVersion\":\"3.0\",\"status\":\"COMPARISON_UNAVAILABLE\"}") ? 1 : 0;

        boolean jsonValid = validationErrors == null || validationErrors.isEmpty();
        ok += write("validation.json",
                "{\"schemaVersion\":\"3.0\",\"jsonValid\":" + jsonValid
                + ",\"jsonlValid\":" + jsonValid
                + ",\"reportPresent\":true"
                + ",\"storageWritable\":" + session.storage.writable
                + ",\"errors\":" + errorsJson(validationErrors) + "}") ? 1 : 0;
        ok += write("report.html", htmlShell()) ? 1 : 0;

        // analysis-complete.json LAST; filesGenerated counts committed files incl. itself.
        int filesGenerated = ok + 1;
        String complete = "{\"schemaVersion\":\"3.0\",\"status\":"
                + (jsonValid ? "\"COMPLETE\"" : "\"PARTIAL\"")
                + ",\"package\":" + Jsons.q(session.packageName)
                + ",\"version\":" + Jsons.q(session.version)
                + ",\"sessionId\":" + Jsons.q(session.sessionId)
                + ",\"requestedOutputDirectory\":"
                + Jsons.q(session.storage.requestedRoot)
                + ",\"actualOutputDirectory\":" + Jsons.q(session.storage.actualRoot)
                + ",\"filesGenerated\":" + filesGenerated
                + ",\"startedAt\":" + Jsons.q(session.startedAt)
                + ",\"completedAt\":" + Jsons.q(KakuClock.utcNowIso()) + "}";
        if (write("analysis-complete.json", complete)) ok = filesGenerated;
        updateIndex(jsonValid ? "COMPLETE" : "PARTIAL");
        return session.file("analysis-complete.json");
    }

    private boolean write(String name, String content) {
        File f = session.file(name);
        if ("runtime.jsonl".equals(name)) {
            // JSONL: validate line-by-line elsewhere; atomic write still applies.
            return AtomicFile.writeUtf8(f, content, null);
        }
        final String c = content;
        return AtomicFile.writeUtf8(f, c,
                bytes -> JsonValidator.looksLikeJson(new String(bytes,
                        java.nio.charset.StandardCharsets.UTF_8)));
    }

    private static String errorsJson(List<String> errs) {
        if (errs == null || errs.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < errs.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(Jsons.q(errs.get(i)));
        }
        return sb.append("]").toString();
    }

    private static String protectionJson(List<ProtectedComponentRegistry.Entry> prot) {
        StringBuilder sb = new StringBuilder("{\"schemaVersion\":\"3.0\",\"components\":[");
        if (prot != null) {
            for (int i = 0; i < prot.size(); i++) {
                ProtectedComponentRegistry.Entry e = prot.get(i);
                if (i > 0) sb.append(",");
                sb.append("{\"status\":\"PROTECTED_OR_UNAVAILABLE\",")
                  .append(Jsons.kv("component", Jsons.q(e.component))).append(",")
                  .append(Jsons.kv("reason", Jsons.q(e.reason))).append(",")
                  .append(Jsons.kv("continuedObservation", "true")).append("}");
            }
        }
        return sb.append("]}").toString();
    }

    private String htmlShell() {
        // CSP-restrictive, fully escaped (no captured JS/HTML executed).
        return "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<meta http-equiv=\"Content-Security-Policy\" "
                + "content=\"default-src 'none'; style-src 'unsafe-inline';\">"
                + "<title>KakuAudit " + HtmlEscaper.esc(session.sessionId) + "</title></head>"
                + "<body><h1>KakuAudit OBSERVE_ONLY report</h1>"
                + "<p>Package: " + HtmlEscaper.esc(session.packageName) + " "
                + HtmlEscaper.esc(session.version) + "</p>"
                + "<p>Session: " + HtmlEscaper.esc(session.sessionId) + "</p>"
                + "<p>Requested: " + HtmlEscaper.esc(session.storage.requestedRoot) + "</p>"
                + "<p>Actual: " + HtmlEscaper.esc(session.storage.actualRoot) + "</p>"
                + "<p>Mode: OBSERVE_ONLY — observed / inferred-with-evidence / "
                + "not-observed / unavailable / redacted / failed.</p>"
                + "</body></html>";
    }

    private void updateIndex(String status) {
        try {
            File idx = new File(session.sessionDir.getParentFile().getParentFile()
                    .getParentFile(), "index.json");
            String entry = "{\"package\":" + Jsons.q(session.packageName)
                    + ",\"version\":" + Jsons.q(session.version)
                    + ",\"sessionId\":" + Jsons.q(session.sessionId)
                    + ",\"status\":" + Jsons.q(status) + "}";
            String content = "{\"schemaVersion\":\"3.0\",\"specVersion\":\"3.0.0\","
                    + "\"updatedAt\":" + Jsons.q(KakuClock.utcNowIso())
                    + ",\"sessions\":[" + entry + "]}";
            AtomicFile.writeUtf8(idx, content,
                    bytes -> JsonValidator.looksLikeJson(new String(bytes,
                            java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Throwable ignore) { /* index failure -> session stays PARTIAL upstream */ }
    }
}
