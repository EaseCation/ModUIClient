package net.easecation.moduiclient.ui.element;

import com.google.gson.JsonObject;
import net.easecation.moduiclient.ui.NineSliceInfo;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

/**
 * Button element with three-state textures (default, hover, pressed) and label.
 * For "buttonSlice" type, textures are rendered with nine-slice stretching.
 */
public class UIElementButton extends UIElement {

    private String defaultTexture;
    private String hoverTexture;
    private String pressedTexture;
    private String buttonLabel = "";
    private Identifier defaultTextureId;
    private Identifier hoverTextureId;
    private Identifier pressedTextureId;

    // Nine-slice info per texture state (lazily loaded)
    private NineSliceInfo defaultNs, hoverNs, pressedNs;
    private boolean nsLoaded = false;

    // Interaction state
    private boolean hovered = false;
    private boolean pressed = false;

    public UIElementButton(String name, String type) {
        super(name, type);
    }

    @Override
    public void initFromJson(JsonObject json) {
        super.initFromJson(json);

        if (json.has("default")) {
            defaultTexture = json.get("default").getAsString();
            defaultTextureId = resolveTexture(defaultTexture);
        }
        if (json.has("hover")) {
            hoverTexture = json.get("hover").getAsString();
            hoverTextureId = resolveTexture(hoverTexture);
        }
        if (json.has("pressed")) {
            pressedTexture = json.get("pressed").getAsString();
            pressedTextureId = resolveTexture(pressedTexture);
        }
        if (json.has("button_label")) {
            buttonLabel = json.get("button_label").getAsString();
        }
    }

    private void ensureNsLoaded() {
        if (!nsLoaded) {
            defaultNs = NineSliceInfo.forTexture(defaultTexture);
            hoverNs = NineSliceInfo.forTexture(hoverTexture);
            pressedNs = NineSliceInfo.forTexture(pressedTexture);
            nsLoaded = true;
        }
    }

    @Override
    public void render(DrawContext context, float tickDelta) {
        if (!isVisible() || getAlpha() <= 0) return;

        int x = (int) getResolvedX();
        int y = (int) getResolvedY();
        int w = (int) getResolvedWidth();
        int h = (int) getResolvedHeight();

        if (w <= 0 || h <= 0) return;

        // Determine which texture and nine-slice info to show
        Identifier activeTexture;
        NineSliceInfo activeNs;
        ensureNsLoaded();

        if (pressed && pressedTextureId != null) {
            activeTexture = pressedTextureId;
            activeNs = pressedNs;
        } else if (hovered && hoverTextureId != null) {
            activeTexture = hoverTextureId;
            activeNs = hoverNs;
        } else {
            activeTexture = defaultTextureId;
            activeNs = defaultNs;
        }

        // Render button background
        if (activeTexture != null) {
            int alpha = (int) (getAlpha() * 255);
            int color = (alpha << 24) | 0xFFFFFF;

            if (activeNs != null && activeNs.isNineSlice() && activeNs.baseWidth > 0 && activeNs.baseHeight > 0) {
                UIElementImage.renderNineSliceStatic(context, activeTexture, x, y, w, h, activeNs, color);
            } else {
                context.drawTexture(RenderPipelines.GUI_TEXTURED, activeTexture, x, y, 0, 0, w, h, w, h, color);
            }
        }

        // Render button label
        if (buttonLabel != null && !buttonLabel.isEmpty()) {
            TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
            int textWidth = textRenderer.getWidth(buttonLabel);
            int textX = x + (w - textWidth) / 2;
            int textY = y + (h - textRenderer.fontHeight) / 2;
            int alpha = (int) (getAlpha() * 255);
            int color = (alpha << 24) | 0xFFFFFF;
            context.drawText(textRenderer, buttonLabel, textX, textY, color, true);
        }
    }

    public void updateHoverState(double mouseX, double mouseY) {
        hovered = containsPoint((float) mouseX, (float) mouseY);
    }

    private static Identifier resolveTexture(String bedrockPath) {
        if (bedrockPath == null || bedrockPath.isEmpty()) return null;
        String path = bedrockPath.toLowerCase(java.util.Locale.ROOT);
        if (!path.endsWith(".png")) path = path + ".png";
        return Identifier.of("minecraft", path);
    }

    // --- Setters for commands ---

    public void setDefaultTexture(String path) {
        this.defaultTexture = path;
        this.defaultTextureId = resolveTexture(path);
        this.nsLoaded = false;
    }

    public void setHoverTexture(String path) {
        this.hoverTexture = path;
        this.hoverTextureId = resolveTexture(path);
        this.nsLoaded = false;
    }

    public void setPressedTexture(String path) {
        this.pressedTexture = path;
        this.pressedTextureId = resolveTexture(path);
        this.nsLoaded = false;
    }

    public void setButtonLabel(String label) { this.buttonLabel = label; }
    public String getButtonLabel() { return buttonLabel; }

    public boolean isHovered() { return hovered; }
    public boolean isPressed() { return pressed; }
    public void setPressed(boolean pressed) { this.pressed = pressed; }
}
