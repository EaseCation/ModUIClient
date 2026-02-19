package net.easecation.moduiclient.ui.element;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Text element with font size scaling, alignment, line wrapping and line padding.
 */
public class UIElementText extends UIElement {

    private static final float BEDROCK_FONT_HEIGHT = 10f;

    private String text = "";
    private float fontSize = 1.0f;
    private boolean textShadow = false;
    private String textAlignment = "center"; // left, center, right (default: center for Bedrock "text" type)
    private float linePadding = 0f; // extra pixels between lines (scaled with fontSize)
    private float colorR = 1f, colorG = 1f, colorB = 1f, colorA = 1f;

    public UIElementText(String name, String type) {
        super(name, type);
        // textLeft/textRight set default alignment based on type
        if ("textLeft".equals(type)) {
            textAlignment = "left";
        } else if ("textRight".equals(type)) {
            textAlignment = "right";
        }
    }

    @Override
    public void initFromJson(JsonObject json) {
        super.initFromJson(json);

        if (json.has("text")) {
            text = json.get("text").getAsString();
        }
        if (json.has("fontSize")) {
            fontSize = json.get("fontSize").getAsFloat();
        }
        if (json.has("textShadow")) {
            textShadow = json.get("textShadow").getAsBoolean();
        }
        if (json.has("textAlignment")) {
            textAlignment = json.get("textAlignment").getAsString();
        }
        if (json.has("line_padding")) {
            linePadding = json.get("line_padding").getAsFloat();
        }
        if (json.has("linePadding")) {
            linePadding = json.get("linePadding").getAsFloat();
        }
        if (json.has("color")) {
            JsonArray color = json.getAsJsonArray("color");
            if (color.size() >= 3) {
                colorR = color.get(0).getAsFloat();
                colorG = color.get(1).getAsFloat();
                colorB = color.get(2).getAsFloat();
                if (color.size() >= 4) {
                    colorA = color.get(3).getAsFloat();
                }
            }
        }
    }

    /**
     * Wrap text into lines, honoring explicit \n and auto-wrapping at maxWidth.
     * @param maxWidth max line width in unscaled pixels, or <= 0 for no wrapping
     */
    private List<String> wrapText(String text, float maxWidth, TextRenderer textRenderer) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) return lines;

        // Split by explicit newlines first
        String[] segments = text.split("\n", -1);

        for (String segment : segments) {
            if (maxWidth <= 0 || textRenderer.getWidth(segment) <= maxWidth) {
                lines.add(segment);
                continue;
            }
            // Auto-wrap this segment
            String remaining = segment;
            while (!remaining.isEmpty()) {
                String trimmed = textRenderer.trimToWidth(remaining, (int) maxWidth);
                if (trimmed.isEmpty()) {
                    // At least one character per line to avoid infinite loop
                    trimmed = remaining.substring(0, 1);
                }
                lines.add(trimmed);
                remaining = remaining.substring(trimmed.length());
            }
        }

        return lines;
    }

    private int getLineCount() {
        if (text == null || text.isEmpty()) return 1;
        // Only wrap when width is explicitly set (sizeX != null)
        if (getSizeX() != null) {
            TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
            float resolvedW = getResolvedWidth();
            if (resolvedW > 0 && fontSize > 0) {
                float wrapWidth = resolvedW / fontSize;
                List<String> lines = wrapText(text, wrapWidth, textRenderer);
                return Math.max(1, lines.size());
            }
        }
        // No wrapping — just count explicit newlines
        return text.split("\n", -1).length;
    }

    @Override
    public float getContentWidth() {
        if (text == null || text.isEmpty()) return 0;
        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        // For multi-line, return the widest line
        String[] segments = text.split("\n", -1);
        float maxW = 0;
        for (String seg : segments) {
            maxW = Math.max(maxW, textRenderer.getWidth(seg));
        }
        return maxW * fontSize;
    }

    @Override
    public float getContentHeight() {
        int lineCount = getLineCount();
        return (lineCount * BEDROCK_FONT_HEIGHT + Math.max(0, lineCount - 1) * linePadding) * fontSize;
    }

    @Override
    public void render(DrawContext context, float tickDelta) {
        if (!isVisible() || getAlpha() <= 0 || text == null || text.isEmpty()) return;

        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;
        int x = (int) getResolvedX();
        int y = (int) getResolvedY();
        int w = (int) getResolvedWidth();

        int alpha = (int) (getAlpha() * colorA * 255);
        int r = (int) (colorR * 255);
        int g = (int) (colorG * 255);
        int b = (int) (colorB * 255);
        int color = (alpha << 24) | (r << 16) | (g << 8) | b;

        // Apply font size scaling
        boolean scaled = fontSize != 1.0f;
        if (scaled) {
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(x, y);
            context.getMatrices().scale(fontSize, fontSize);
            context.getMatrices().translate(-x, -y);
        }

        // Wrap width: only auto-wrap when width is explicitly set (sizeX != null)
        float alignWidth = scaled ? w / fontSize : w; // always use resolved width for alignment
        float wrapWidth = (getSizeX() != null && w > 0) ? alignWidth : -1;
        List<String> lines = wrapText(text, wrapWidth, textRenderer);
        if (lines.isEmpty()) lines = List.of(text);

        // Center Java's 9px font within Bedrock's 10px content height
        float yAdjust = (BEDROCK_FONT_HEIGHT - textRenderer.fontHeight) / 2f;

        // Line step: Java fontHeight + linePadding (in unscaled pixels)
        float lineStep = textRenderer.fontHeight + linePadding;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isEmpty()) continue;

            int lineY = y + Math.round(yAdjust + i * lineStep);
            int lineWidth = textRenderer.getWidth(line);

            int drawX = switch (textAlignment) {
                case "center" -> x + (int) ((alignWidth - lineWidth) / 2);
                case "right" -> x + (int) (alignWidth - lineWidth);
                default -> x;
            };

            context.drawText(textRenderer, line, drawX, lineY, color, textShadow);
        }

        if (scaled) {
            context.getMatrices().popMatrix();
        }
    }

    // --- Setters for commands ---

    public void setText(String text) { this.text = text; }
    public String getText() { return text; }

    public void setFontSize(float fontSize) { this.fontSize = fontSize; }
    public float getFontSize() { return fontSize; }

    public void setTextColor(float r, float g, float b, float a) {
        this.colorR = r;
        this.colorG = g;
        this.colorB = b;
        this.colorA = a;
    }

    public void setLinePadding(float padding) { this.linePadding = padding; }
    public void setTextAlignment(String alignment) { this.textAlignment = alignment; }
}
