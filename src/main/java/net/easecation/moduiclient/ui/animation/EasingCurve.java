package net.easecation.moduiclient.ui.animation;

import net.easecation.moduiclient.ModUIClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Easing curves for UI animations.
 * Provides 6 built-in curves and custom N-order Bezier curves.
 *
 * All curves map rate ∈ [0,1] → progress ∈ [0,1].
 *
 * Reference: AnimationControl.py CURVES dict + BezierCurve.py
 */
public abstract class EasingCurve {

    /** Evaluate the curve at the given rate (0 to 1). */
    public abstract float evaluate(float rate);

    // --- Built-in curves (singleton instances, zero allocation) ---

    public static final EasingCurve LINEAR = new EasingCurve() {
        @Override public float evaluate(float t) { return t; }
        @Override public String toString() { return "LINEAR"; }
    };

    public static final EasingCurve EASE_IN = new EasingCurve() {
        @Override public float evaluate(float t) { return t * t; }
        @Override public String toString() { return "EASE_IN"; }
    };

    public static final EasingCurve EASE_OUT = new EasingCurve() {
        @Override public float evaluate(float t) {
            float inv = 1f - t;
            return 1f - inv * inv;
        }
        @Override public String toString() { return "EASE_OUT"; }
    };

    public static final EasingCurve EASE_IN_OUT_SIN = new EasingCurve() {
        @Override public float evaluate(float t) {
            return (float) ((Math.sin(Math.PI / 2.0 * (2.0 * t - 1.0)) + 1.0) / 2.0);
        }
        @Override public String toString() { return "EASE_IN_OUT_SIN"; }
    };

    public static final EasingCurve SIN_GO_AND_BACK = new EasingCurve() {
        @Override public float evaluate(float t) {
            return (float) Math.sin(Math.PI * t);
        }
        @Override public String toString() { return "SIN_GO_AND_BACK"; }
    };

    public static final EasingCurve STEP = new EasingCurve() {
        @Override public float evaluate(float t) { return t < 1f ? 0f : 1f; }
        @Override public String toString() { return "STEP"; }
    };

    // --- Bezier curve cache ---

    private static final Map<String, EasingCurve> BEZIER_CACHE = new HashMap<>();

    /**
     * Parse a curve name string into an EasingCurve.
     * Supports built-in names and "bezier[(x0,y0),(x1,y1),...]" format.
     */
    public static EasingCurve parse(String curveName) {
        if (curveName == null || curveName.isEmpty()) return LINEAR;

        return switch (curveName.toUpperCase(java.util.Locale.ROOT)) {
            case "LINEAR" -> LINEAR;
            case "EASE_IN" -> EASE_IN;
            case "EASE_OUT" -> EASE_OUT;
            case "EASE_IN_OUT_SIN", "EASE_IN_OUT_SINE" -> EASE_IN_OUT_SIN;
            case "SIN_GO_AND_BACK" -> SIN_GO_AND_BACK;
            case "STEP" -> STEP;
            default -> {
                if (curveName.startsWith("bezier")) {
                    yield parseBezier(curveName);
                }
                ModUIClient.LOGGER.warn("[EasingCurve] Unknown curve '{}', falling back to LINEAR", curveName);
                yield LINEAR;
            }
        };
    }

    private static EasingCurve parseBezier(String spec) {
        EasingCurve cached = BEZIER_CACHE.get(spec);
        if (cached != null) return cached;

        try {
            float[][] points = parseControlPoints(spec);
            if (points.length < 2) {
                ModUIClient.LOGGER.warn("[EasingCurve] Bezier needs at least 2 control points: {}", spec);
                return LINEAR;
            }
            BezierCurve curve = new BezierCurve(points);
            BEZIER_CACHE.put(spec, curve);
            return curve;
        } catch (Exception e) {
            ModUIClient.LOGGER.warn("[EasingCurve] Failed to parse bezier '{}': {}", spec, e.getMessage());
            return LINEAR;
        }
    }

    /**
     * Parse "bezier[(x0,y0),(x1,y1),...]" into float[][2].
     */
    private static float[][] parseControlPoints(String spec) {
        // Extract content between outermost [ and ]
        int start = spec.indexOf('[');
        int end = spec.lastIndexOf(']');
        if (start < 0 || end < 0 || end <= start) {
            throw new IllegalArgumentException("Invalid bezier format: " + spec);
        }
        String inner = spec.substring(start + 1, end);

        List<float[]> points = new ArrayList<>();
        int i = 0;
        while (i < inner.length()) {
            // Find next (x,y) pair
            int pStart = inner.indexOf('(', i);
            if (pStart < 0) break;
            int pEnd = inner.indexOf(')', pStart);
            if (pEnd < 0) break;

            String pair = inner.substring(pStart + 1, pEnd);
            String[] parts = pair.split(",");
            if (parts.length >= 2) {
                float x = Float.parseFloat(parts[0].trim());
                float y = Float.parseFloat(parts[1].trim());
                points.add(new float[]{x, y});
            }
            i = pEnd + 1;
        }

        return points.toArray(new float[0][]);
    }

    /**
     * N-order Bezier curve with pre-computed lookup table.
     * Uses De Casteljau's algorithm for evaluation.
     */
    static class BezierCurve extends EasingCurve {
        private static final int LUT_SIZE = 256;
        private final float[] lutX; // x values (input axis)
        private final float[] lutY; // y values (output axis)

        BezierCurve(float[][] controlPoints) {
            lutX = new float[LUT_SIZE + 1];
            lutY = new float[LUT_SIZE + 1];

            // Pre-compute LUT by uniformly sampling parameter t ∈ [0, 1]
            for (int i = 0; i <= LUT_SIZE; i++) {
                float t = (float) i / LUT_SIZE;
                lutX[i] = deCasteljau(controlPoints, 0, t);
                lutY[i] = deCasteljau(controlPoints, 1, t);
            }
        }

        @Override
        public float evaluate(float rate) {
            if (rate <= 0f) return lutY[0];
            if (rate >= 1f) return lutY[LUT_SIZE];

            // Binary search for rate in lutX
            int lo = 0, hi = LUT_SIZE;
            while (lo < hi - 1) {
                int mid = (lo + hi) >>> 1;
                if (lutX[mid] <= rate) {
                    lo = mid;
                } else {
                    hi = mid;
                }
            }

            // Linear interpolation between lo and hi
            float xLo = lutX[lo], xHi = lutX[hi];
            float yLo = lutY[lo], yHi = lutY[hi];
            float span = xHi - xLo;
            if (span < 1e-6f) return yLo;
            float frac = (rate - xLo) / span;
            return yLo + frac * (yHi - yLo);
        }

        /**
         * De Casteljau's algorithm to evaluate one component of an N-order Bezier curve.
         * @param points control points
         * @param component 0 for x, 1 for y
         * @param t parameter [0, 1]
         */
        private static float deCasteljau(float[][] points, int component, float t) {
            int n = points.length;
            // Copy component values into work array
            float[] work = new float[n];
            for (int i = 0; i < n; i++) {
                work[i] = points[i][component];
            }
            // Iteratively reduce
            for (int level = n - 1; level > 0; level--) {
                for (int i = 0; i < level; i++) {
                    work[i] = work[i] * (1f - t) + work[i + 1] * t;
                }
            }
            return work[0];
        }

        @Override
        public String toString() { return "BezierCurve"; }
    }

    public static void clearCache() {
        BEZIER_CACHE.clear();
    }
}
