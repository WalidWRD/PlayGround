package com.kakuaudit.observe.core;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Spec §13: bounded queue + backpressure + sampling + drop accounting.
 * Never blocks the target app indefinitely; drops are counted and reported.
 */
public final class EventBus {
    public interface Sink {
        void write(String jsonLine);
    }

    private final BlockingQueue<String> queue;
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong accepted = new AtomicLong();
    private volatile double sampleRate;
    private final Sink sink;
    private final Thread worker;
    private volatile boolean closed;

    // Priority markers: security/lifecycle/error/state events bypass sampling.
    public static boolean isPriority(String jsonLine) {
        return jsonLine != null && (jsonLine.contains("PROTECTION_OBSERVATION")
                || jsonLine.contains("LIFECYCLE")
                || jsonLine.contains("CRASH")
                || jsonLine.contains("STATE_TRANSITION")
                || jsonLine.contains("RESOURCE_LIMIT_REACHED"));
    }

    public EventBus(int maxQueueItems, double sampleRate, Sink sink) {
        this.queue = new ArrayBlockingQueue<>(maxQueueItems);
        this.sampleRate = sampleRate;
        this.sink = sink;
        this.worker = new Thread(() -> {
            while (!closed || !queue.isEmpty()) {
                try {
                    String e = queue.take();
                    try {
                        sink.write(e);
                    } catch (Throwable ignore) {
                        dropped.incrementAndGet();
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }, "KakuAudit-EventBus");
        this.worker.setDaemon(true);
        this.worker.start();
    }

    /** Non-blocking publish. Returns false when dropped/sampled. */
    public boolean publish(String jsonLine, long seq) {
        if (jsonLine == null) return false;
        if (jsonLine.length() > 65536) {
            dropped.incrementAndGet(); // maxSingleEventBytes
            return false;
        }
        if (!isPriority(jsonLine) && sampleRate < 1.0) {
            // Deterministic sampling on sequence to stay testable.
            double pick = ((seq * 2654435761L) & 0xFFFFFFFFL) / (double) 0xFFFFFFFFL;
            if (pick > sampleRate) {
                dropped.incrementAndGet();
                return false;
            }
        }
        boolean ok = queue.offer(jsonLine);
        if (ok) accepted.incrementAndGet();
        else dropped.incrementAndGet();
        return ok;
    }

    public long dropped() { return dropped.get(); }
    public long accepted() { return accepted.get(); }
    public int pending() { return queue.size(); }

    public void close() {
        closed = true;
        worker.interrupt();
    }
}
