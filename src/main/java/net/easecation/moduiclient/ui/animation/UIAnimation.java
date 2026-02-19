package net.easecation.moduiclient.ui.animation;

import com.google.gson.JsonObject;
import net.easecation.moduiclient.ModUIClient;
import net.easecation.moduiclient.ui.element.UIElement;
import net.easecation.moduiclient.ui.element.UIElementImage;
import net.easecation.moduiclient.ui.layout.SizeExpression;

/**
 * A single property animation instance.
 * Corresponds to Python AnimationControl — holds configuration, timing state,
 * and applies interpolated values to the target element each frame.
 *
 * Interpolation formula: value_start + curve(rate) * (value_end - value_start)
 *
 * Reference: AnimationControl.py on_tick(), FullPositionSetter, FullSizeSetter, AlphaSetter, RotateSetter
 */
public class UIAnimation {

    /** Result of a single tick, used by AnimationManager to determine actions. */
    public enum TickResult {
        /** No layout impact (alpha/rotate) */
        NONE,
        /** Position/size changed, layout needs refresh */
        LAYOUT,
        /** Animation completed, should be removed */
        FINISHED
    }

    public enum AnimationType {
        POSITION, SIZE, ALPHA, ROTATE
    }

    // --- Immutable configuration (set at construction) ---

    private final AnimationType type;
    private final String axis;          // "x" or "y", for POSITION/SIZE
    private final boolean relative;     // true = modify relativeValue (÷100), false = modify absoluteValue
    private final float valueStart;
    private final float valueEnd;
    private final float durationNanos;  // pre-converted to nanoseconds
    private final float delayNanos;     // pre-converted to nanoseconds
    private final boolean loop;
    private final EasingCurve curve;

    // --- Mutable runtime state ---

    private final long createdAtNanos;
    private long startedAtNanos;        // -1 = still in delay phase
    private boolean finished = false;

    // Rotate animation: capture initial angle on first tick
    private float initialRotateAngle;
    private boolean initialRotateCaptured = false;

    public UIAnimation(AnimationType type, String axis, boolean relative,
                       float valueStart, float valueEnd,
                       float durationSeconds, float delaySeconds,
                       boolean loop, EasingCurve curve) {
        this.type = type;
        this.axis = axis;
        this.relative = relative;
        this.valueStart = valueStart;
        this.valueEnd = valueEnd;
        this.durationNanos = durationSeconds * 1_000_000_000f;
        this.delayNanos = delaySeconds * 1_000_000_000f;
        this.loop = loop;
        this.curve = curve;
        this.createdAtNanos = System.nanoTime();
        this.startedAtNanos = -1;
    }

    /**
     * Tick this animation for one frame.
     * Updates the target element's property and returns the tick result.
     *
     * @param element the target UI element
     * @param nowNanos current time from System.nanoTime()
     * @return TickResult indicating what happened
     */
    public TickResult tick(UIElement element, long nowNanos) {
        if (finished) return TickResult.FINISHED;

        // Handle delay phase
        if (startedAtNanos < 0) {
            if ((nowNanos - createdAtNanos) < (long) delayNanos) {
                return TickResult.NONE; // Still waiting
            }
            startedAtNanos = nowNanos;
            captureInitialState(element);
        }

        float elapsed = (float) (nowNanos - startedAtNanos);
        float rate = durationNanos > 0 ? elapsed / durationNanos : 1f;

        if (rate < 1f) {
            // In progress — interpolate
            float clampedRate = Math.max(0f, Math.min(1f, rate));
            float curvedRate = curve.evaluate(clampedRate);
            float value = valueStart + curvedRate * (valueEnd - valueStart);
            return applyValue(element, value);
        } else {
            // Reached end
            TickResult result = applyValue(element, valueEnd);
            if (loop) {
                startedAtNanos = nowNanos; // Restart
                return affectsLayout() ? TickResult.LAYOUT : TickResult.NONE;
            } else {
                finished = true;
                // Return FINISHED but also mark layout if needed
                // (the final value was already applied above)
                return TickResult.FINISHED;
            }
        }
    }

    public boolean isFinished() {
        return finished;
    }

    /** Whether this animation type requires layout recalculation. */
    private boolean affectsLayout() {
        return type == AnimationType.POSITION || type == AnimationType.SIZE;
    }

    /** Capture initial state needed for relative animations (e.g., rotate initial angle). */
    private void captureInitialState(UIElement element) {
        if (type == AnimationType.ROTATE && element instanceof UIElementImage img) {
            initialRotateAngle = img.getRotateAngle();
            initialRotateCaptured = true;
        }
    }

    /**
     * Apply the interpolated value to the target element's property.
     * Returns the appropriate TickResult based on animation type.
     */
    private TickResult applyValue(UIElement element, float value) {
        switch (type) {
            case POSITION -> {
                boolean isX = "x".equalsIgnoreCase(axis);
                SizeExpression expr = isX ? element.getPositionX() : element.getPositionY();
                if (expr == null) {
                    // Create a new expression if none exists (element had "default" position)
                    expr = SizeExpression.absolute(0);
                    if (isX) element.setPositionX(expr);
                    else element.setPositionY(expr);
                }
                if (relative) {
                    expr.setRelativeValue(value / 100f);
                } else {
                    expr.setAbsoluteValue(value);
                }
                return TickResult.LAYOUT;
            }
            case SIZE -> {
                boolean isX = "x".equalsIgnoreCase(axis);
                SizeExpression expr = isX ? element.getSizeX() : element.getSizeY();
                if (expr == null) {
                    expr = SizeExpression.absolute(0);
                    if (isX) element.setSizeX(expr);
                    else element.setSizeY(expr);
                }
                if (relative) {
                    expr.setRelativeValue(value / 100f);
                } else {
                    expr.setAbsoluteValue(value);
                }
                return TickResult.LAYOUT;
            }
            case ALPHA -> {
                element.setAlpha(value);
                return TickResult.NONE;
            }
            case ROTATE -> {
                if (element instanceof UIElementImage img) {
                    if (initialRotateCaptured) {
                        img.setRotateAngle(initialRotateAngle + value);
                    } else {
                        img.setRotateAngle(value);
                    }
                }
                return TickResult.NONE;
            }
        }
        return TickResult.NONE;
    }

    /**
     * Create a UIAnimation from a JSON configuration object.
     *
     * Expected fields:
     *   type (string): "position", "size", "alpha", "rotate"
     *   duration (float): seconds
     *   delay (float, optional): seconds, default 0
     *   loop (boolean, optional): default false
     *   value_start (float): start value
     *   value_end (float): end value
     *   curve (string, optional): easing curve name, default "LINEAR"
     *   axis (string, optional): "x" or "y", for position/size
     *   relative (boolean, optional): for position/size
     */
    public static UIAnimation fromJson(JsonObject json) {
        String typeStr = json.get("type").getAsString();
        AnimationType type = switch (typeStr.toLowerCase(java.util.Locale.ROOT)) {
            case "position" -> AnimationType.POSITION;
            case "size" -> AnimationType.SIZE;
            case "alpha" -> AnimationType.ALPHA;
            case "rotate" -> AnimationType.ROTATE;
            default -> throw new IllegalArgumentException("Unknown animation type: " + typeStr);
        };

        float duration = json.get("duration").getAsFloat();
        float delay = json.has("delay") ? json.get("delay").getAsFloat() : 0f;
        boolean loop = json.has("loop") && json.get("loop").getAsBoolean();
        float valueStart = json.get("value_start").getAsFloat();
        float valueEnd = json.get("value_end").getAsFloat();

        String curveStr = json.has("curve") ? json.get("curve").getAsString() : "LINEAR";
        EasingCurve curve = EasingCurve.parse(curveStr);

        String axis = json.has("axis") ? json.get("axis").getAsString() : "x";
        boolean relative = json.has("relative") && json.get("relative").getAsBoolean();

        return new UIAnimation(type, axis, relative, valueStart, valueEnd,
                duration, delay, loop, curve);
    }
}
