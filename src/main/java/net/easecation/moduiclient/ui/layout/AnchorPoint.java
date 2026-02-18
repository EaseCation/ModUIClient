package net.easecation.moduiclient.ui.layout;

/**
 * 9-point anchor system for UI element positioning.
 * Maps anchor names to normalized (0-1) coordinates.
 */
public enum AnchorPoint {

    TOP_LEFT(0f, 0f),
    TOP_MIDDLE(0.5f, 0f),
    TOP_RIGHT(1f, 0f),
    LEFT_MIDDLE(0f, 0.5f),
    CENTER(0.5f, 0.5f),
    RIGHT_MIDDLE(1f, 0.5f),
    BOTTOM_LEFT(0f, 1f),
    BOTTOM_MIDDLE(0.5f, 1f),
    BOTTOM_RIGHT(1f, 1f);

    private final float x;
    private final float y;

    AnchorPoint(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public float getX() { return x; }
    public float getY() { return y; }

    public static AnchorPoint fromString(String name) {
        if (name == null || name.isEmpty()) return CENTER;
        return switch (name.toLowerCase()) {
            case "top_left" -> TOP_LEFT;
            case "top_middle" -> TOP_MIDDLE;
            case "top_right" -> TOP_RIGHT;
            case "left_middle" -> LEFT_MIDDLE;
            case "center" -> CENTER;
            case "right_middle" -> RIGHT_MIDDLE;
            case "bottom_left" -> BOTTOM_LEFT;
            case "bottom_middle" -> BOTTOM_MIDDLE;
            case "bottom_right" -> BOTTOM_RIGHT;
            default -> CENTER;
        };
    }
}
