package net.easecation.moduiclient.ui.element;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.easecation.moduiclient.ModUIClient;
import net.easecation.moduiclient.ui.layout.SizeExpression;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Scroll view element — a clipped viewport with scrollable content.
 *
 * The element's own resolved size is the viewport; content can be larger.
 * Children are laid out relative to content dimensions, offset by scrollOffset.
 * A scrollbar is rendered on the right side when content exceeds viewport.
 *
 * Reference: ECBaseUI.py scroll handling (line 450-467),
 *            ModUIElementScroll.java (server-side element definition)
 */
public class UIElementScroll extends UIElement {

    // Content size expressions (parsed from JSON "contentSize")
    private SizeExpression contentSizeExprX;
    private SizeExpression contentSizeExprY;

    // Resolved content dimensions (may be larger than viewport)
    private float resolvedContentWidth;
    private float resolvedContentHeight;

    // Current vertical scroll offset (0 = top)
    private float scrollOffset = 0;

    // Scrollbar visual constants
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SCROLLBAR_PADDING = 2;
    private static final int THUMB_MIN_HEIGHT = 20;
    private static final int TRACK_COLOR = 0x40FFFFFF;    // semi-transparent white
    private static final int THUMB_COLOR = 0x80FFFFFF;    // brighter white
    private static final int THUMB_HOVER_COLOR = 0xA0FFFFFF;

    // Scrollbar auto-hide constants
    private static final long FADE_IN_DURATION_MS = 150;
    private static final long FADE_OUT_DURATION_MS = 300;
    private static final long INACTIVITY_DELAY_MS = 1500;
    private static final float HOVER_ZONE_EXTEND = 20f;

    // Scrollbar drag state (managed by ModUIStackScreen)
    private boolean thumbHovered = false;

    // Scrollbar auto-hide state
    private float scrollbarAlpha = 0f;
    private float scrollbarAlphaTarget = 0f;
    private long lastActivityNanos = 0;
    private long lastFrameNanos = 0;
    private boolean mouseNearScrollbar = false;
    private boolean scrollbarDragging = false;

    public UIElementScroll(String name, String type) {
        super(name, type);
        setClip(true); // always clip
    }

    @Override
    public void initFromJson(JsonObject json) {
        super.initFromJson(json);
        setClip(true); // enforce clip

        // Parse contentSize
        if (json.has("contentSize")) {
            JsonArray cs = json.getAsJsonArray("contentSize");
            if (cs.size() >= 2) {
                contentSizeExprX = SizeExpression.parse(cs.get(0).getAsString());
                contentSizeExprY = SizeExpression.parse(cs.get(1).getAsString());
            }
        }

        ModUIClient.LOGGER.info("[UIElementScroll] initFromJson '{}': contentSizeX={}, contentSizeY={}, sizeX={}, sizeY={}",
                getName(), contentSizeExprX, contentSizeExprY, getSizeX(), getSizeY());

        // Parse initial scroll position
        if (json.has("viewPos")) {
            scrollOffset = json.get("viewPos").getAsFloat();
        } else if (json.has("viewPosPercent")) {
            // Will be applied after content size is resolved
            // Store as negative to indicate "pending percent"
            scrollOffset = -(json.get("viewPosPercent").getAsInt() + 1);
        }
    }

    @Override
    public void renderTree(DrawContext context, float tickDelta) {
        if (!isVisible()) return;

        // Set up scissor for viewport
        int cx = (int) getResolvedX();
        int cy = (int) getResolvedY();
        int cw = (int) getResolvedWidth();
        int ch = (int) getResolvedHeight();
        context.enableScissor(cx, cy, cx + cw, cy + ch);

        // Render children (their positions already account for scroll offset via layout)
        List<UIElement> sortedChildren = new ArrayList<>(getChildren());
        sortedChildren.sort((a, b) -> Integer.compare(a.getLayer(), b.getLayer()));
        for (UIElement child : sortedChildren) {
            child.renderTree(context, tickDelta);
        }

        context.disableScissor();

        // Render scrollbar (outside scissor, overlays content)
        renderScrollbar(context);
    }

    private void renderScrollbar(DrawContext context) {
        float maxScroll = getMaxScrollOffset();
        if (maxScroll <= 0) return; // no scrollbar needed

        updateScrollbarAlpha();
        if (scrollbarAlpha <= 0.001f) return; // fully hidden

        float viewportH = getResolvedHeight();
        float scrollX = getResolvedX() + getResolvedWidth() - SCROLLBAR_WIDTH - SCROLLBAR_PADDING;
        float scrollY = getResolvedY() + SCROLLBAR_PADDING;
        float trackH = viewportH - SCROLLBAR_PADDING * 2;

        // Track background
        context.fill(
                (int) scrollX, (int) scrollY,
                (int) (scrollX + SCROLLBAR_WIDTH), (int) (scrollY + trackH),
                modulateAlpha(TRACK_COLOR, scrollbarAlpha)
        );

        // Thumb
        float thumbH = Math.max(THUMB_MIN_HEIGHT, (viewportH / resolvedContentHeight) * trackH);
        float thumbY = scrollY + (scrollOffset / maxScroll) * (trackH - thumbH);

        int thumbColor = thumbHovered ? THUMB_HOVER_COLOR : THUMB_COLOR;
        context.fill(
                (int) scrollX, (int) thumbY,
                (int) (scrollX + SCROLLBAR_WIDTH), (int) (thumbY + thumbH),
                modulateAlpha(thumbColor, scrollbarAlpha)
        );
    }

    private void updateScrollbarAlpha() {
        long now = System.nanoTime();
        float deltaSeconds = lastFrameNanos == 0 ? (1f / 60f) : (now - lastFrameNanos) / 1_000_000_000f;
        deltaSeconds = Math.min(deltaSeconds, 0.1f);
        lastFrameNanos = now;

        // Determine if we should start fading out
        if (!mouseNearScrollbar && !scrollbarDragging && scrollbarAlphaTarget > 0f) {
            long elapsedMs = (now - lastActivityNanos) / 1_000_000L;
            if (elapsedMs >= INACTIVITY_DELAY_MS) {
                scrollbarAlphaTarget = 0f;
            }
        }

        // Interpolate toward target
        if (scrollbarAlpha < scrollbarAlphaTarget) {
            float speed = 1.0f / (FADE_IN_DURATION_MS / 1000f);
            scrollbarAlpha = Math.min(scrollbarAlphaTarget, scrollbarAlpha + speed * deltaSeconds);
        } else if (scrollbarAlpha > scrollbarAlphaTarget) {
            float speed = 1.0f / (FADE_OUT_DURATION_MS / 1000f);
            scrollbarAlpha = Math.max(scrollbarAlphaTarget, scrollbarAlpha - speed * deltaSeconds);
        }
    }

    private static int modulateAlpha(int color, float alphaMul) {
        int a = (color >>> 24) & 0xFF;
        int rgb = color & 0x00FFFFFF;
        int newA = Math.round(a * alphaMul);
        return (Math.max(0, Math.min(255, newA)) << 24) | rgb;
    }

    // --- Scroll position ---

    public float getMaxScrollOffset() {
        return Math.max(0, resolvedContentHeight - getResolvedHeight());
    }

    public void setScrollOffset(float offset) {
        this.scrollOffset = Math.max(0, Math.min(offset, getMaxScrollOffset()));
    }

    /**
     * Apply pending percent scroll position (called after layout resolves content size).
     */
    public void applyPendingScrollPercent() {
        if (scrollOffset < 0) {
            // Decode pending percent (stored as -(percent + 1))
            int percent = (int) (-scrollOffset - 1);
            float maxScroll = getMaxScrollOffset();
            this.scrollOffset = maxScroll * percent / 100f;
        }
    }

    public void setScrollPercent(int percent) {
        float maxScroll = getMaxScrollOffset();
        this.scrollOffset = Math.max(0, Math.min(maxScroll * percent / 100f, maxScroll));
    }

    public float getScrollOffset() { return scrollOffset; }

    // --- Scrollbar hit testing ---

    /**
     * Get the scrollbar track bounds (for mouse interaction).
     * Returns {x, y, width, height} or null if no scrollbar.
     */
    public float[] getTrackBounds() {
        if (getMaxScrollOffset() <= 0) return null;
        float x = getResolvedX() + getResolvedWidth() - SCROLLBAR_WIDTH - SCROLLBAR_PADDING;
        float y = getResolvedY() + SCROLLBAR_PADDING;
        float h = getResolvedHeight() - SCROLLBAR_PADDING * 2;
        return new float[]{x, y, SCROLLBAR_WIDTH, h};
    }

    /**
     * Get the thumb bounds within the track.
     * Returns {x, y, width, height} or null if no scrollbar.
     */
    public float[] getThumbBounds() {
        float maxScroll = getMaxScrollOffset();
        if (maxScroll <= 0) return null;
        float[] track = getTrackBounds();
        if (track == null) return null;
        float trackH = track[3];
        float thumbH = Math.max(THUMB_MIN_HEIGHT, (getResolvedHeight() / resolvedContentHeight) * trackH);
        float thumbY = track[1] + (scrollOffset / maxScroll) * (trackH - thumbH);
        return new float[]{track[0], thumbY, SCROLLBAR_WIDTH, thumbH};
    }

    /**
     * Check if a point is on the scrollbar thumb.
     */
    public boolean isPointOnThumb(float x, float y) {
        float[] thumb = getThumbBounds();
        if (thumb == null) return false;
        return x >= thumb[0] && x < thumb[0] + thumb[2]
                && y >= thumb[1] && y < thumb[1] + thumb[3];
    }

    /**
     * Check if a point is on the scrollbar track (but not thumb).
     */
    public boolean isPointOnTrack(float x, float y) {
        float[] track = getTrackBounds();
        if (track == null) return false;
        return x >= track[0] && x < track[0] + track[2]
                && y >= track[1] && y < track[1] + track[3]
                && !isPointOnThumb(x, y);
    }

    /**
     * Handle track click — page scroll toward click position.
     */
    public void handleTrackClick(float mouseY) {
        float[] thumb = getThumbBounds();
        if (thumb == null) return;
        float thumbCenter = thumb[1] + thumb[3] / 2;
        float pageAmount = getResolvedHeight() * 0.8f;
        if (mouseY < thumbCenter) {
            setScrollOffset(scrollOffset - pageAmount);
        } else {
            setScrollOffset(scrollOffset + pageAmount);
        }
    }

    /**
     * Convert a drag delta on the scrollbar track to a scroll offset delta.
     */
    public float trackDeltaToScrollDelta(float trackDy) {
        float[] track = getTrackBounds();
        if (track == null) return 0;
        float trackH = track[3];
        float thumbH = Math.max(THUMB_MIN_HEIGHT, (getResolvedHeight() / resolvedContentHeight) * trackH);
        float scrollableTrack = trackH - thumbH;
        if (scrollableTrack <= 0) return 0;
        return trackDy * getMaxScrollOffset() / scrollableTrack;
    }

    public void setThumbHovered(boolean hovered) { this.thumbHovered = hovered; }

    // --- Scrollbar auto-hide ---

    public void notifyScrollbarActivity() {
        lastActivityNanos = System.nanoTime();
        scrollbarAlphaTarget = 1.0f;
    }

    public boolean isPointNearScrollbar(float x, float y) {
        float[] track = getTrackBounds();
        if (track == null) return false;
        float extendedX = track[0] - HOVER_ZONE_EXTEND;
        return x >= extendedX && x < track[0] + track[2]
                && y >= track[1] && y < track[1] + track[3];
    }

    public void setMouseNearScrollbar(boolean near) {
        boolean wasNear = this.mouseNearScrollbar;
        this.mouseNearScrollbar = near;
        if (near && !wasNear) {
            notifyScrollbarActivity();
        } else if (!near && wasNear) {
            lastActivityNanos = System.nanoTime();
        }
    }

    public void setScrollbarDragging(boolean dragging) {
        this.scrollbarDragging = dragging;
        if (dragging) {
            notifyScrollbarActivity();
        }
    }

    public boolean isScrollbarInteractable() {
        return scrollbarAlpha >= 0.1f;
    }

    // --- Content size ---

    public SizeExpression getContentSizeExprX() { return contentSizeExprX; }
    public SizeExpression getContentSizeExprY() { return contentSizeExprY; }
    public void setContentSizeExprX(SizeExpression expr) { this.contentSizeExprX = expr; }
    public void setContentSizeExprY(SizeExpression expr) { this.contentSizeExprY = expr; }

    public float getResolvedContentWidth() { return resolvedContentWidth; }
    public float getResolvedContentHeight() { return resolvedContentHeight; }
    public void setResolvedContentWidth(float w) { this.resolvedContentWidth = w; }
    public void setResolvedContentHeight(float h) { this.resolvedContentHeight = h; }
}
