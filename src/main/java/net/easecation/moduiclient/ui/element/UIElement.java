package net.easecation.moduiclient.ui.element;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.easecation.moduiclient.ui.layout.AnchorPoint;
import net.easecation.moduiclient.ui.layout.SizeExpression;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for all ModUI elements.
 * Corresponds to ModUIElement in Nukkit's NetEaseModNukkitUI.
 */
public class UIElement {

    // Identity
    private final String name;
    private final String type;
    private String fullPath; // e.g. "/bg_panel/title"

    // Tree structure
    private UIElement parent;
    private final List<UIElement> children = new ArrayList<>();

    // Layout properties (from JSON)
    private SizeExpression sizeX;
    private SizeExpression sizeY;
    private SizeExpression positionX;
    private SizeExpression positionY;
    private AnchorPoint anchorFrom = AnchorPoint.TOP_LEFT;
    private AnchorPoint anchorTo = AnchorPoint.TOP_LEFT;
    private float maxWidth = 0;
    private float maxHeight = 0;
    private float minWidth = 0;
    private float minHeight = 0;

    // Stack panel orientation ("vertical" or "horizontal")
    private String orientation = "vertical";

    // Visual properties
    private boolean visible = true;
    private float alpha = 1.0f;
    private int layer = 0;
    private boolean clip = false;
    private float clipOffsetX = 0;
    private float clipOffsetY = 0;

    // Resolved layout (absolute screen coordinates)
    private float resolvedX = 0;
    private float resolvedY = 0;
    private float resolvedWidth = 0;
    private float resolvedHeight = 0;

    public UIElement(String name, String type) {
        this.name = name;
        this.type = type;
    }

    /**
     * Initialize element properties from JSON.
     * Subclasses should override and call super.
     */
    public void initFromJson(JsonObject json) {
        // Size
        if (json.has("size")) {
            JsonArray size = json.getAsJsonArray("size");
            if (size.size() >= 2) {
                sizeX = SizeExpression.parse(size.get(0).getAsString());
                sizeY = SizeExpression.parse(size.get(1).getAsString());
            }
        }

        // Position
        if (json.has("position")) {
            JsonArray pos = json.getAsJsonArray("position");
            if (pos.size() >= 2) {
                positionX = SizeExpression.parse(pos.get(0).getAsString());
                positionY = SizeExpression.parse(pos.get(1).getAsString());
            }
        }

        // Anchors
        if (json.has("anchorFrom")) {
            anchorFrom = AnchorPoint.fromString(json.get("anchorFrom").getAsString());
        }
        if (json.has("anchorTo")) {
            anchorTo = AnchorPoint.fromString(json.get("anchorTo").getAsString());
        }

        // Visibility
        if (json.has("visible")) {
            visible = json.get("visible").getAsBoolean();
        }

        // Alpha
        if (json.has("alpha")) {
            alpha = json.get("alpha").getAsFloat();
        }

        // Layer
        if (json.has("layer")) {
            layer = json.get("layer").getAsInt();
        }

        // Max/Min size
        if (json.has("maxSize")) {
            JsonArray maxSize = json.getAsJsonArray("maxSize");
            if (maxSize.size() >= 2) {
                maxWidth = maxSize.get(0).getAsFloat();
                maxHeight = maxSize.get(1).getAsFloat();
            }
        }
        if (json.has("minSize")) {
            JsonArray minSize = json.getAsJsonArray("minSize");
            if (minSize.size() >= 2) {
                minWidth = minSize.get(0).getAsFloat();
                minHeight = minSize.get(1).getAsFloat();
            }
        }

        // Orientation (for stack_panel)
        if (json.has("orientation")) {
            orientation = json.get("orientation").getAsString();
        }

        // Clip
        if (json.has("clip")) {
            clip = json.get("clip").getAsBoolean();
        }
        if (json.has("clipOffset")) {
            JsonArray co = json.getAsJsonArray("clipOffset");
            if (co.size() >= 2) {
                clipOffsetX = co.get(0).getAsFloat();
                clipOffsetY = co.get(1).getAsFloat();
            }
        }
    }

    /**
     * Return intrinsic content width, or -1 if not applicable.
     * Text elements override this to return text pixel width.
     */
    public float getContentWidth() { return -1; }

    /**
     * Return intrinsic content height, or -1 if not applicable.
     * Text elements override this to return text pixel height.
     */
    public float getContentHeight() { return -1; }

    /**
     * Render this element. Subclasses should override.
     */
    public void render(DrawContext context, float tickDelta) {
        if (!visible || alpha <= 0) return;
        // Subclasses implement rendering
    }

    /**
     * Render this element and all visible children, sorted by layer.
     */
    public void renderTree(DrawContext context, float tickDelta) {
        if (!visible) return;

        if (clip) {
            int cx = (int) (resolvedX + clipOffsetX);
            int cy = (int) (resolvedY + clipOffsetY);
            int cw = (int) resolvedWidth;
            int ch = (int) resolvedHeight;
            context.enableScissor(cx, cy, cx + cw, cy + ch);
        }

        render(context, tickDelta);

        // Render children sorted by layer
        List<UIElement> sortedChildren = new ArrayList<>(children);
        sortedChildren.sort((a, b) -> Integer.compare(a.layer, b.layer));
        for (UIElement child : sortedChildren) {
            child.renderTree(context, tickDelta);
        }

        if (clip) {
            context.disableScissor();
        }
    }

    /**
     * Check if a point (screen coordinates) is within this element's bounds.
     */
    public boolean containsPoint(float x, float y) {
        return x >= resolvedX && x < resolvedX + resolvedWidth
                && y >= resolvedY && y < resolvedY + resolvedHeight;
    }

    // --- Factory ---

    /**
     * Create a UIElement from a JSON definition.
     */
    public static UIElement fromJson(JsonObject json) {
        String name = json.has("name") ? json.get("name").getAsString() : "";
        String type = json.has("type") ? json.get("type").getAsString() : "panel";

        UIElement element = switch (type) {
            case "image", "imageElongate", "imageTop" -> new UIElementImage(name, type);
            case "text", "textLeft", "textRight" -> new UIElementText(name, type);
            case "button", "buttonSlice" -> new UIElementButton(name, type);
            case "scroll" -> new UIElementScroll(name, type);
            case "paperDoll" -> new UIElementPaperDoll(name, type);
            case "draggable" -> new UIElementDraggable(name, type);
            default -> new UIElement(name, type); // panel, stack_panel, or unknown type
        };

        element.initFromJson(json);
        return element;
    }

    // --- Tree operations ---

    public void addChild(UIElement child) {
        children.add(child);
        child.parent = this;
        child.fullPath = (this.fullPath != null ? this.fullPath : "") + "/" + child.name;
    }

    public void removeChild(UIElement child) {
        children.remove(child);
        child.parent = null;
    }

    public UIElement findByPath(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) return this;

        String[] parts = path.startsWith("/") ? path.substring(1).split("/") : path.split("/");
        UIElement current = this;
        for (String part : parts) {
            if (part.isEmpty()) continue;
            UIElement found = null;
            for (UIElement child : current.children) {
                if (part.equals(child.name)) {
                    found = child;
                    break;
                }
            }
            if (found == null) return null;
            current = found;
        }
        return current;
    }

    // --- Position helpers ---

    // --- Getters/Setters ---

    public String getName() { return name; }
    public String getType() { return type; }
    public String getFullPath() { return fullPath; }
    public void setFullPath(String fullPath) { this.fullPath = fullPath; }

    public UIElement getParent() { return parent; }
    public List<UIElement> getChildren() { return children; }

    public SizeExpression getSizeX() { return sizeX; }
    public SizeExpression getSizeY() { return sizeY; }
    public void setSizeX(SizeExpression sizeX) { this.sizeX = sizeX; }
    public void setSizeY(SizeExpression sizeY) { this.sizeY = sizeY; }

    public SizeExpression getPositionX() { return positionX; }
    public SizeExpression getPositionY() { return positionY; }
    public void setPositionX(SizeExpression positionX) { this.positionX = positionX; }
    public void setPositionY(SizeExpression positionY) { this.positionY = positionY; }

    public AnchorPoint getAnchorFrom() { return anchorFrom; }
    public AnchorPoint getAnchorTo() { return anchorTo; }
    public void setAnchorFrom(AnchorPoint anchorFrom) { this.anchorFrom = anchorFrom; }
    public void setAnchorTo(AnchorPoint anchorTo) { this.anchorTo = anchorTo; }

    public float getMaxWidth() { return maxWidth; }
    public float getMaxHeight() { return maxHeight; }
    public float getMinWidth() { return minWidth; }
    public float getMinHeight() { return minHeight; }
    public void setMaxWidth(float maxWidth) { this.maxWidth = maxWidth; }
    public void setMaxHeight(float maxHeight) { this.maxHeight = maxHeight; }
    public void setMinWidth(float minWidth) { this.minWidth = minWidth; }
    public void setMinHeight(float minHeight) { this.minHeight = minHeight; }

    public boolean isVisible() { return visible; }
    public void setVisible(boolean visible) { this.visible = visible; }
    public float getAlpha() { return alpha; }
    public void setAlpha(float alpha) { this.alpha = alpha; }
    public int getLayer() { return layer; }
    public void setLayer(int layer) { this.layer = layer; }

    public String getOrientation() { return orientation; }
    public void setOrientation(String orientation) { this.orientation = orientation; }

    public boolean isClip() { return clip; }
    public void setClip(boolean clip) { this.clip = clip; }
    public void setClipOffset(float x, float y) { this.clipOffsetX = x; this.clipOffsetY = y; }

    public float getResolvedX() { return resolvedX; }
    public float getResolvedY() { return resolvedY; }
    public float getResolvedWidth() { return resolvedWidth; }
    public float getResolvedHeight() { return resolvedHeight; }
    public void setResolvedX(float x) { this.resolvedX = x; }
    public void setResolvedY(float y) { this.resolvedY = y; }
    public void setResolvedWidth(float w) { this.resolvedWidth = w; }
    public void setResolvedHeight(float h) { this.resolvedHeight = h; }
}
