package net.easecation.moduiclient.ui.layout;

/**
 * Parses and resolves ModUI size/position expressions.
 *
 * Supported formats:
 *   "100"          → absolute pixels
 *   "default"      → null (use element default size)
 *   "50%"          → parent percentage
 *   "100%c"        → children total size percentage
 *   "100%cm"       → max children size percentage
 *   "100%sm"       → max sibling size percentage
 *   "100%x"        → x-axis reference
 *   "100%y"        → y-axis reference
 *   "100% + 10"    → arithmetic combinations
 *   "100% - 25 - 5" → multi-term subtraction
 *
 * Reference: ECBaseUI.py genParmDict()
 */
public class SizeExpression {

    public enum FollowType {
        NONE,
        PARENT,       // %
        CHILDREN,     // %c
        MAX_CHILDREN, // %cm
        MAX_SIBLING,  // %sm
        X_AXIS,       // %x
        Y_AXIS        // %y
    }

    private final FollowType followType;
    private final float relativeValue; // percentage as fraction (50% = 0.5)
    private float absoluteValue; // pixel offset (mutable for drag position updates)

    private SizeExpression(FollowType followType, float relativeValue, float absoluteValue) {
        this.followType = followType;
        this.relativeValue = relativeValue;
        this.absoluteValue = absoluteValue;
    }

    /**
     * Create a pure absolute pixel value expression.
     */
    public static SizeExpression absolute(float value) {
        return new SizeExpression(FollowType.NONE, 0, value);
    }

    /**
     * Parse a size/position expression string.
     * @return SizeExpression, or null if the value is "default"
     */
    public static SizeExpression parse(String value) {
        if (value == null || value.isEmpty() || "default".equalsIgnoreCase(value.trim())) {
            return null;
        }

        value = value.trim();
        FollowType followType = FollowType.NONE;
        float relativeValue = 0;
        float absoluteValue = 0;

        // Split by + and - while preserving the operator
        // Normalize: remove spaces around operators
        String normalized = value.replaceAll("\\s*\\+\\s*", "+").replaceAll("\\s*-\\s*", "+-");

        String[] parts = normalized.split("\\+");
        for (String part : parts) {
            part = part.trim();
            if (part.isEmpty()) continue;

            if (part.contains("%")) {
                // Parse percentage part
                int percentIdx = part.indexOf('%');
                String percentStr = part.substring(0, percentIdx);
                String suffix = part.substring(percentIdx + 1);

                float pct = Float.parseFloat(percentStr) / 100f;

                FollowType ft = switch (suffix) {
                    case "c" -> FollowType.CHILDREN;
                    case "cm" -> FollowType.MAX_CHILDREN;
                    case "sm" -> FollowType.MAX_SIBLING;
                    case "x" -> FollowType.X_AXIS;
                    case "y" -> FollowType.Y_AXIS;
                    default -> FollowType.PARENT; // "" or any other suffix
                };

                followType = ft;
                relativeValue = pct;
            } else {
                // Absolute pixel value
                absoluteValue += Float.parseFloat(part);
            }
        }

        return new SizeExpression(followType, relativeValue, absoluteValue);
    }

    /**
     * Resolve the expression to an absolute pixel value.
     */
    public float resolve(float parentSize) {
        return resolve(parentSize, 0, 0, 0, 0, 0);
    }

    /**
     * Resolve the expression with full context.
     */
    public float resolve(float parentSize, float childrenSize, float maxChildSize,
                         float maxSiblingSize, float xRef, float yRef) {
        float base = switch (followType) {
            case PARENT -> parentSize;
            case CHILDREN -> childrenSize;
            case MAX_CHILDREN -> maxChildSize;
            case MAX_SIBLING -> maxSiblingSize;
            case X_AXIS -> xRef;
            case Y_AXIS -> yRef;
            case NONE -> 0;
        };
        return base * relativeValue + absoluteValue;
    }

    public FollowType getFollowType() { return followType; }
    public float getRelativeValue() { return relativeValue; }
    public float getAbsoluteValue() { return absoluteValue; }
    public void setAbsoluteValue(float absoluteValue) { this.absoluteValue = absoluteValue; }

    @Override
    public String toString() {
        if (followType == FollowType.NONE) return String.valueOf(absoluteValue);
        return (relativeValue * 100) + "%" + followType + (absoluteValue != 0 ? " + " + absoluteValue : "");
    }
}
