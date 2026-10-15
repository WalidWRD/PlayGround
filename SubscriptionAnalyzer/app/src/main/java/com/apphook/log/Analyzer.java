package com.apphook.log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * نواة القياس: تُجمِّع جميع الأحداث في تقرير JSON واحد.
 * خيط-آمنة وتعمل في المسارين (حديث + legacy).
 */
public final class Analyzer {
    private Analyzer() {}

    public static final class Event {
        public final String category, event, original, patched;
        public final int argCount;
        public final boolean changed;
        public final long ts;

        Event(String category, String event, int argCount,
              String original, String patched, boolean changed) {
            this.category = category;
            this.event = event;
            this.argCount = argCount;
            this.original = original;
            this.patched = patched;
            this.changed = changed;
            this.ts = System.currentTimeMillis();
        }
    }

    private static final List<Event> events = new ArrayList<>();
    private static final Map<String, AtomicInteger> opens = new ConcurrentHashMap<>();
    private static final long startTime = System.currentTimeMillis();
    private static final int REPORT_EVERY = 100;

    public static void record(String category, String event,
                              int argCount, String orig,
                              String patched, boolean changed) {
        boolean flush = false;
        synchronized (Analyzer.class) {
            events.add(new Event(category, event, argCount, orig, patched, changed));
            flush = (events.size() % REPORT_EVERY == 0);
        }
        if (flush) ReportWriter.writeThrottled();
    }

    public static void recordOpen(String feature, String code) {
        AtomicInteger c = opens.get(code + ":" + feature);
        if (c == null) {
            c = new AtomicInteger(0);
            opens.put(code + ":" + feature, c);
        }
        c.incrementAndGet();
        ReportWriter.writeThrottled();
    }

    public static synchronized String toJson() {
        StringBuilder sb = new StringBuilder(65536);
        sb.append("{\n");
        sb.append("  \"module\": \"SubscriptionAnalyzer\",\n");
        sb.append("  \"version\": \"6.0.0\",\n");
        sb.append("  \"mode\": \"").append(Config.modeName()).append("\",\n");
        sb.append("  \"package\": \"").append(escape(Config.runtimePackage)).append("\",\n");
        sb.append("  \"startedAt\": ").append(startTime).append(",\n");
        sb.append("  \"durationMs\": ").append(System.currentTimeMillis() - startTime).append(",\n");
        int changed;
        int total;
        synchronized (Analyzer.class) {
            total = events.size();
            int n = 0;
            for (Event e : events) if (e.changed) n++;
            changed = n;
        }
        sb.append("  \"totalEvents\": ").append(total).append(",\n");
        sb.append("  \"patchedEvents\": ").append(changed).append(",\n");

        sb.append("  \"featuresOpened\": {\n");
        int i = 0;
        for (Map.Entry<String, AtomicInteger> e : opens.entrySet()) {
            if (i > 0) sb.append(",\n");
            sb.append("    \"").append(escape(e.getKey())).append("\": ")
                    .append(e.getValue().get());
            i++;
        }
        sb.append("\n  },\n");

        sb.append("  \"events\": [\n");
        synchronized (Analyzer.class) {
            for (int j = 0; j < events.size(); j++) {
                Event ev = events.get(j);
                sb.append("    {\"t\": ").append(ev.ts)
                        .append(", \"cat\": \"").append(escape(ev.category))
                        .append("\", \"ev\": \"").append(escape(ev.event))
                        .append("\", \"args\": ").append(ev.argCount)
                        .append(", \"orig\": \"").append(escape(ev.original))
                        .append("\", \"patched\": \"").append(escape(ev.patched))
                        .append("\", \"changed\": ").append(ev.changed)
                        .append("}");
                if (j < events.size() - 1) sb.append(",");
                sb.append("\n");
            }
        }
        sb.append("  ]\n");
        sb.append("}\n");
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
