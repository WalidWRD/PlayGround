package com.kakuaudit.observe.core;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Spec §11: numeric confidence + qualitative level + evidence basis. */
public final class Confidence {
    public enum Level {
        WEAK, POSSIBLE, MODERATE, STRONG, DIRECT_AND_REPEATED, UNASSESSED
    }

    private Confidence() {}

    public static Level levelFor(double v) {
        if (v < 0) return Level.UNASSESSED;
        if (v <= 0.24) return Level.WEAK;
        if (v <= 0.49) return Level.POSSIBLE;
        if (v <= 0.74) return Level.MODERATE;
        if (v <= 0.94) return Level.STRONG;
        if (v <= 1.0) return Level.DIRECT_AND_REPEATED;
        return Level.UNASSESSED;
    }

    /** Static-name-only hint: never above 0.24 (spec §1.1 / §11.1). */
    public static double capForStaticHint(double v) {
        return Math.min(v, 0.24);
    }

    public static List<String> basis(String... items) {
        return Collections.unmodifiableList(Arrays.asList(items));
    }
}
