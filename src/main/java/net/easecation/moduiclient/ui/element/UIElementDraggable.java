package net.easecation.moduiclient.ui.element;

import com.google.gson.JsonObject;
import net.easecation.moduiclient.ModUIClient;
import net.easecation.moduiclient.ui.layout.AnchorPoint;
import net.easecation.moduiclient.ui.layout.SizeExpression;

/**
 * Draggable container element — allows a child node to be dragged within bounds.
 *
 * The element itself is a panel-like container. One of its children (specified by
 * draggableNodeName) can be moved by mouse drag. The drag is constrained to
 * automatically computed or explicitly configured boundaries.
 *
 * Reference: ECBaseUI.py draggable handling (line 478-693),
 *            ModUIElementDraggable.java (server-side element definition)
 */
public class UIElementDraggable extends UIElement {

    // Name of the child element that gets dragged
    private String draggableNodeName;

    // Drag state
    private double lastMouseX = Double.NaN;
    private double lastMouseY = Double.NaN;

    // Boundary state
    private boolean borderInitialized = false;
    private boolean useCustomBoundary = false;
    private float borderXMin = 0;
    private Float borderXMax = null;
    private float borderYMin = 0;
    private Float borderYMax = null;

    public UIElementDraggable(String name, String type) {
        super(name, type);
    }

    @Override
    public void initFromJson(JsonObject json) {
        super.initFromJson(json);

        // Parse draggableNode name
        if (json.has("draggableNode")) {
            draggableNodeName = json.get("draggableNode").getAsString();
        }

        // Parse optional custom boundary
        if (json.has("draggableBoundary")) {
            JsonObject boundary = json.getAsJsonObject("draggableBoundary");
            useCustomBoundary = true;
            if (boundary.has("minX")) borderXMin = boundary.get("minX").getAsFloat();
            if (boundary.has("maxX")) borderXMax = boundary.get("maxX").getAsFloat();
            if (boundary.has("minY")) borderYMin = boundary.get("minY").getAsFloat();
            if (boundary.has("maxY")) borderYMax = boundary.get("maxY").getAsFloat();
            borderInitialized = true;
        }

        // Parse optional initial position
        if (json.has("draggablePosition")) {
            JsonObject pos = json.getAsJsonObject("draggablePosition");
            float x = pos.has("x") ? pos.get("x").getAsFloat() : 0;
            float y = pos.has("y") ? pos.get("y").getAsFloat() : 0;
            // Will be applied when the target node is available (after tree construction)
            // Store for later application
            initialPosX = x;
            initialPosY = y;
            hasInitialPosition = true;
        }
    }

    private float initialPosX, initialPosY;
    private boolean hasInitialPosition = false;

    /**
     * Apply initial position to the target node (called after tree is built).
     */
    public void applyInitialPosition() {
        if (!hasInitialPosition || draggableNodeName == null) return;
        UIElement node = findDraggableNode();
        if (node == null) return;
        if (node.getPositionX() == null) node.setPositionX(SizeExpression.absolute(0));
        if (node.getPositionY() == null) node.setPositionY(SizeExpression.absolute(0));
        node.getPositionX().setAbsoluteValue(initialPosX);
        node.getPositionY().setAbsoluteValue(initialPosY);
        hasInitialPosition = false;
        ModUIClient.LOGGER.debug("[Draggable] Initial position set for {}: ({}, {})",
                draggableNodeName, initialPosX, initialPosY);
    }

    // --- Drag operations ---

    /**
     * Begin a drag operation.
     */
    public void beginDrag(double mouseX, double mouseY) {
        lastMouseX = mouseX;
        lastMouseY = mouseY;
    }

    /**
     * Process a drag movement. Returns true if position was updated.
     */
    public boolean processDrag(double mouseX, double mouseY) {
        if (Double.isNaN(lastMouseX)) return false;

        UIElement node = findDraggableNode();
        if (node == null) return false;

        // Initialize boundary on first drag
        if (!borderInitialized) {
            initializeBoundary(node);
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return false; // skip first frame (matches Python behavior)
        }

        float dx = (float) (mouseX - lastMouseX);
        float dy = (float) (mouseY - lastMouseY);

        // Ensure position expressions exist
        if (node.getPositionX() == null) node.setPositionX(SizeExpression.absolute(0));
        if (node.getPositionY() == null) node.setPositionY(SizeExpression.absolute(0));

        float currentX = node.getPositionX().getAbsoluteValue();
        float currentY = node.getPositionY().getAbsoluteValue();

        float newX = currentX + dx;
        float newY = currentY + dy;

        // Apply boundary constraints
        if (borderXMax != null) {
            newX = Math.max(borderXMin, Math.min(newX, borderXMax));
        }
        if (borderYMax != null) {
            newY = Math.max(borderYMin, Math.min(newY, borderYMax));
        }

        node.getPositionX().setAbsoluteValue(newX);
        node.getPositionY().setAbsoluteValue(newY);

        lastMouseX = mouseX;
        lastMouseY = mouseY;
        return true;
    }

    /**
     * End the drag operation.
     */
    public void endDrag() {
        lastMouseX = Double.NaN;
        lastMouseY = Double.NaN;
    }

    // --- Boundary calculation ---

    /**
     * Initialize boundary from container and node sizes + anchors.
     * Matches Python ECBaseUI.py line 543-630.
     */
    private void initializeBoundary(UIElement node) {
        if (useCustomBoundary) {
            borderInitialized = true;
            return;
        }

        float containerW = getResolvedWidth();
        float containerH = getResolvedHeight();
        float nodeW = node.getResolvedWidth();
        float nodeH = node.getResolvedHeight();

        AnchorPoint anchorFrom = node.getAnchorFrom();
        AnchorPoint anchorTo = node.getAnchorTo();

        // Anchor offsets relative to element top-left
        float anchorFromX = anchorFrom.getX() * nodeW;
        float anchorFromY = anchorFrom.getY() * nodeH;
        float anchorToX = anchorTo.getX() * containerW;
        float anchorToY = anchorTo.getY() * containerH;

        // Boundary calculation (matches Python formula)
        float posXMin = (containerW - nodeW) + (anchorFromX - anchorToX);
        float posXMax = anchorFromX - anchorToX;
        float posYMin = (containerH - nodeH) + (anchorFromY - anchorToY);
        float posYMax = anchorFromY - anchorToY;

        // Ensure min <= max
        if (posXMin > posXMax) { float t = posXMin; posXMin = posXMax; posXMax = t; }
        if (posYMin > posYMax) { float t = posYMin; posYMin = posYMax; posYMax = t; }

        borderXMin = posXMin;
        borderXMax = posXMax;
        borderYMin = posYMin;
        borderYMax = posYMax;
        borderInitialized = true;

        ModUIClient.LOGGER.debug("[Draggable] Boundary: X[{}, {}], Y[{}, {}] (from={}, to={})",
                borderXMin, borderXMax, borderYMin, borderYMax, anchorFrom, anchorTo);
    }

    /**
     * Reset boundary so it will be recalculated on next drag.
     */
    public void resetBoundary() {
        borderInitialized = false;
        useCustomBoundary = false;
        borderXMax = null;
        borderYMax = null;
    }

    /**
     * Set custom boundary explicitly.
     */
    public void setCustomBoundary(float minX, float maxX, float minY, float maxY) {
        useCustomBoundary = true;
        borderXMin = minX;
        borderXMax = maxX;
        borderYMin = minY;
        borderYMax = maxY;
        borderInitialized = true;
    }

    /**
     * Set the target node's position directly.
     */
    public void setDraggablePosition(float x, float y) {
        UIElement node = findDraggableNode();
        if (node == null) return;
        if (node.getPositionX() == null) node.setPositionX(SizeExpression.absolute(0));
        if (node.getPositionY() == null) node.setPositionY(SizeExpression.absolute(0));
        node.getPositionX().setAbsoluteValue(x);
        node.getPositionY().setAbsoluteValue(y);
    }

    // --- Node lookup ---

    /**
     * Find the draggable target node by name among children.
     */
    public UIElement findDraggableNode() {
        if (draggableNodeName == null) return null;
        return findByName(this, draggableNodeName);
    }

    private static UIElement findByName(UIElement parent, String name) {
        for (UIElement child : parent.getChildren()) {
            if (name.equals(child.getName())) return child;
            UIElement found = findByName(child, name);
            if (found != null) return found;
        }
        return null;
    }

    // --- Getters ---

    public String getDraggableNodeName() { return draggableNodeName; }
    public boolean isDragging() { return !Double.isNaN(lastMouseX); }
}
