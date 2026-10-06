package com.kakuaudit.observe.core;

/**
 * Hard safety boundary: this module is OBSERVE_ONLY.
 * Any code path that would modify args / result / app state / transport
 * must abort and record UNSUPPORTED_OBSERVATION instead.
 */
public final class ObserveMode {
    public static final String MODE = "OBSERVE_ONLY";
    public static final String SPEC_VERSION = "3.0.0";
    public static final int SCHEMA_MAJOR = 3;

    private ObserveMode() {}

    /** Guard helper: never allow bypass paths to proceed silently. */
    public static void failClosed(String reason) {
        throw new UnsupportedObservationException(reason);
    }

    public static final class UnsupportedObservationException extends RuntimeException {
        public UnsupportedObservationException(String reason) {
            super("UNSUPPORTED_OBSERVATION: " + reason);
        }
    }
}
