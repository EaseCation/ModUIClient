package net.easecation.moduiclient.ui.layout;

import net.easecation.moduiclient.ModUIClient;
import net.easecation.moduiclient.ui.element.UIElement;
import net.easecation.moduiclient.ui.element.UIElementScroll;

/**
 * Layout engine that resolves element positions and sizes
 * based on the ModUI anchor + percentage size system.
 *
 * Layout calculation:
 *   finalX = parent.anchorTo.x * parent.w - element.anchorFrom.x * element.w + position.x
 *   finalY = parent.anchorTo.y * parent.h - element.anchorFrom.y * element.h + position.y
 *
 * stack_panel / stackPanel: children stacked sequentially along orientation axis.
 *   - Children's position property is IGNORED (Bedrock native behavior).
 *   - When size is "default" (null SizeExpression), the panel is sized to fit its children.
 *
 * "default" size (null SizeExpression) for non-stack_panel elements:
 *   - Text elements: auto-size to text content
 *   - Other elements: inherit parent size (100%)
 *
 * scroll: viewport size resolved first, then children, then content dimensions.
 *   - Children's position offset by -scrollOffset during position resolution.
 */
public class LayoutEngine {

    public static void resolveTree(UIElement root) {
        resolveSize(root);
        resolvePosition(root, 0, 0);
    }

    private static boolean isStackPanel(UIElement element) {
        String type = element.getType();
        return "stack_panel".equals(type) || "stackPanel".equals(type);
    }

    private static boolean isHorizontal(UIElement element) {
        String o = element.getOrientation();
        return "horizontal".equals(o) || "h".equals(o);
    }

    private static void resolveSize(UIElement element) {
        // Scroll elements have their own resolution logic (already top-down)
        if (element instanceof UIElementScroll scroll) {
            resolveScrollSize(scroll);
            return;
        }

        // --- Top-down resolution ---
        // 1. Resolve self FIRST so children can use our resolved dimensions as parent
        float parentW = element.getParent() != null ? element.getParent().getResolvedWidth() : element.getResolvedWidth();
        float parentH = element.getParent() != null ? element.getParent().getResolvedHeight() : element.getResolvedHeight();

        float maxSiblingW = 0, maxSiblingH = 0;
        if (element.getParent() != null) {
            for (UIElement sibling : element.getParent().getChildren()) {
                if (sibling != element) {
                    maxSiblingW = Math.max(maxSiblingW, sibling.getResolvedWidth());
                    maxSiblingH = Math.max(maxSiblingH, sibling.getResolvedHeight());
                }
            }
        }

        // Initial resolve without children stats (children not resolved yet)
        resolveElementSize(element, parentW, parentH, 0, 0, 0, 0, maxSiblingW, maxSiblingH);

        // 2. Resolve children (they can now use our resolved dimensions)
        for (UIElement child : element.getChildren()) {
            resolveSize(child);
        }

        // 3. Re-resolve if element size depends on children stats
        //    (stack panel default size, or %c/%cm expressions)
        if (needsChildrenResolve(element)) {
            float childrenW = 0, childrenH = 0;
            float maxChildW = 0, maxChildH = 0;
            for (UIElement child : element.getChildren()) {
                childrenW += child.getResolvedWidth();
                childrenH += child.getResolvedHeight();
                maxChildW = Math.max(maxChildW, child.getResolvedWidth());
                maxChildH = Math.max(maxChildH, child.getResolvedHeight());
            }
            resolveElementSize(element, parentW, parentH, childrenW, childrenH,
                    maxChildW, maxChildH, maxSiblingW, maxSiblingH);
        }
    }

    /**
     * Check if an element's size depends on children statistics
     * and needs a second resolve pass after children are resolved.
     */
    private static boolean needsChildrenResolve(UIElement element) {
        // Stack panels with default (null) size auto-size from children
        if (isStackPanel(element) && (element.getSizeX() == null || element.getSizeY() == null)) {
            return true;
        }
        // Elements using %c or %cm follow types
        SizeExpression sizeX = element.getSizeX();
        if (sizeX != null && (sizeX.getFollowType() == SizeExpression.FollowType.CHILDREN
                || sizeX.getFollowType() == SizeExpression.FollowType.MAX_CHILDREN)) {
            return true;
        }
        SizeExpression sizeY = element.getSizeY();
        if (sizeY != null && (sizeY.getFollowType() == SizeExpression.FollowType.CHILDREN
                || sizeY.getFollowType() == SizeExpression.FollowType.MAX_CHILDREN)) {
            return true;
        }
        return false;
    }

    /**
     * Resolve an element's width and height from its size expressions and context.
     */
    private static void resolveElementSize(UIElement element,
                                           float parentW, float parentH,
                                           float childrenW, float childrenH,
                                           float maxChildW, float maxChildH,
                                           float maxSiblingW, float maxSiblingH) {
        boolean stackPanel = isStackPanel(element);

        // Resolve width
        SizeExpression sizeX = element.getSizeX();
        if (sizeX != null) {
            float w = sizeX.resolve(parentW, childrenW, maxChildW, maxSiblingW, 0, 0);
            if (element.getMaxWidth() > 0) w = Math.min(w, element.getMaxWidth());
            if (element.getMinWidth() > 0) w = Math.max(w, element.getMinWidth());
            element.setResolvedWidth(w);
        } else if (stackPanel) {
            float w = isHorizontal(element) ? childrenW : maxChildW;
            if (element.getMaxWidth() > 0) w = Math.min(w, element.getMaxWidth());
            if (element.getMinWidth() > 0) w = Math.max(w, element.getMinWidth());
            element.setResolvedWidth(w);
        } else if (element.getParent() != null) {
            float contentW = element.getContentWidth();
            element.setResolvedWidth(contentW >= 0 ? contentW : parentW);
        }

        // Resolve height
        SizeExpression sizeY = element.getSizeY();
        if (sizeY != null) {
            float h = sizeY.resolve(parentH, childrenH, maxChildH, maxSiblingH, 0, 0);
            if (element.getMaxHeight() > 0) h = Math.min(h, element.getMaxHeight());
            if (element.getMinHeight() > 0) h = Math.max(h, element.getMinHeight());
            element.setResolvedHeight(h);
        } else if (stackPanel) {
            float h = isHorizontal(element) ? maxChildH : childrenH;
            if (element.getMaxHeight() > 0) h = Math.min(h, element.getMaxHeight());
            if (element.getMinHeight() > 0) h = Math.max(h, element.getMinHeight());
            element.setResolvedHeight(h);
        } else if (element.getParent() != null) {
            float contentH = element.getContentHeight();
            element.setResolvedHeight(contentH >= 0 ? contentH : parentH);
        }
    }

    /**
     * Special size resolution for scroll elements.
     * Order: resolve self viewport → resolve children → compute content dimensions.
     */
    private static void resolveScrollSize(UIElementScroll scroll) {
        // 1. Resolve scroll's own viewport size (using parent dimensions)
        float parentW = scroll.getParent() != null ? scroll.getParent().getResolvedWidth() : scroll.getResolvedWidth();
        float parentH = scroll.getParent() != null ? scroll.getParent().getResolvedHeight() : scroll.getResolvedHeight();

        float maxSiblingW = 0, maxSiblingH = 0;
        if (scroll.getParent() != null) {
            for (UIElement sibling : scroll.getParent().getChildren()) {
                if (sibling != scroll) {
                    maxSiblingW = Math.max(maxSiblingW, sibling.getResolvedWidth());
                    maxSiblingH = Math.max(maxSiblingH, sibling.getResolvedHeight());
                }
            }
        }

        // Resolve viewport size (no children stats needed for viewport itself)
        resolveElementSize(scroll, parentW, parentH, 0, 0, 0, 0, maxSiblingW, maxSiblingH);

        ModUIClient.LOGGER.info("[LayoutEngine] Scroll '{}': parentW={}, parentH={}, viewport={}x{}",
                scroll.getName(), parentW, parentH, scroll.getResolvedWidth(), scroll.getResolvedHeight());

        // 2. Resolve children (they use viewport dimensions as parent)
        for (UIElement child : scroll.getChildren()) {
            resolveSize(child);
        }

        // 3. Collect children statistics
        float childrenW = 0, childrenH = 0;
        float maxChildW = 0, maxChildH = 0;
        for (UIElement child : scroll.getChildren()) {
            childrenW += child.getResolvedWidth();
            childrenH += child.getResolvedHeight();
            maxChildW = Math.max(maxChildW, child.getResolvedWidth());
            maxChildH = Math.max(maxChildH, child.getResolvedHeight());
            ModUIClient.LOGGER.info("[LayoutEngine]   Scroll '{}' child '{}': resolved={}x{}",
                    scroll.getName(), child.getName(), child.getResolvedWidth(), child.getResolvedHeight());
        }

        // 4. Resolve content dimensions
        float viewportW = scroll.getResolvedWidth();
        float viewportH = scroll.getResolvedHeight();

        SizeExpression contentExprX = scroll.getContentSizeExprX();
        float contentW = contentExprX != null
                ? contentExprX.resolve(viewportW, childrenW, maxChildW, maxSiblingW, 0, 0)
                : viewportW;

        SizeExpression contentExprY = scroll.getContentSizeExprY();
        float contentH = contentExprY != null
                ? contentExprY.resolve(viewportH, childrenH, maxChildH, maxSiblingH, 0, 0)
                : viewportH;

        scroll.setResolvedContentWidth(contentW);
        scroll.setResolvedContentHeight(contentH);

        ModUIClient.LOGGER.info("[LayoutEngine] Scroll '{}': content={}x{}, contentExprX={}, contentExprY={}, childrenW={}, childrenH={}, maxChildW={}, maxChildH={}",
                scroll.getName(), contentW, contentH, contentExprX, contentExprY, childrenW, childrenH, maxChildW, maxChildH);

        // Apply pending percent scroll position
        scroll.applyPendingScrollPercent();
        // Clamp scroll offset to valid range
        scroll.setScrollOffset(scroll.getScrollOffset());
    }

    private static void resolvePosition(UIElement element, float parentAbsX, float parentAbsY) {
        if (element.getParent() != null) {
            // Scroll children use VIEWPORT dimensions for anchor/position (not content dimensions).
            // Content dimensions are only used for scrollbar and maxScrollOffset.
            // The scroll offset is applied via parentAbsY, not via anchor calculations.
            float parentW = element.getParent().getResolvedWidth();
            float parentH = element.getParent().getResolvedHeight();

            // NetEase UI: anchorFrom = reference point on PARENT, anchorTo = reference point on ELEMENT
            AnchorPoint parentAnchor = element.getAnchorFrom();
            AnchorPoint elementAnchor = element.getAnchorTo();

            float baseX = parentAnchor.getX() * parentW - elementAnchor.getX() * element.getResolvedWidth();
            float baseY = parentAnchor.getY() * parentH - elementAnchor.getY() * element.getResolvedHeight();

            SizeExpression posX = element.getPositionX();
            SizeExpression posY = element.getPositionY();
            float offsetX = posX != null ? posX.resolve(parentW) : 0;
            float offsetY = posY != null ? posY.resolve(parentH) : 0;

            element.setResolvedX(parentAbsX + baseX + offsetX);
            element.setResolvedY(parentAbsY + baseY + offsetY);

            if (element.getParent() instanceof UIElementScroll) {
                ModUIClient.LOGGER.info("[LayoutEngine] ScrollChild '{}': parentAbsXY=({},{}), viewportWH=({},{}), anchor=({},{})->({},{}), base=({},{}), offset=({},{}), resolved=({},{}), size={}x{}",
                        element.getName(), parentAbsX, parentAbsY, parentW, parentH,
                        parentAnchor.getX(), parentAnchor.getY(), elementAnchor.getX(), elementAnchor.getY(),
                        baseX, baseY, offsetX, offsetY,
                        element.getResolvedX(), element.getResolvedY(),
                        element.getResolvedWidth(), element.getResolvedHeight());
            }
        }

        if (element instanceof UIElementScroll scroll) {
            // Scroll children are offset by -scrollOffset
            float contentAbsX = scroll.getResolvedX();
            float contentAbsY = scroll.getResolvedY() - scroll.getScrollOffset();
            for (UIElement child : scroll.getChildren()) {
                resolvePosition(child, contentAbsX, contentAbsY);
            }
        } else if (isStackPanel(element)) {
            resolveStackChildren(element);
        } else {
            for (UIElement child : element.getChildren()) {
                resolvePosition(child, element.getResolvedX(), element.getResolvedY());
            }
        }
    }

    private static void resolveStackChildren(UIElement stackPanel) {
        boolean horizontal = isHorizontal(stackPanel);
        float stackX = stackPanel.getResolvedX();
        float stackY = stackPanel.getResolvedY();
        float stackW = stackPanel.getResolvedWidth();
        float stackH = stackPanel.getResolvedHeight();

        float cursor = 0;

        for (UIElement child : stackPanel.getChildren()) {
            if (horizontal) {
                float childX = stackX + cursor;
                AnchorPoint parentAnchor = child.getAnchorFrom();
                AnchorPoint elementAnchor = child.getAnchorTo();
                float crossBase = parentAnchor.getY() * stackH - elementAnchor.getY() * child.getResolvedHeight();
                float childY = stackY + crossBase;

                child.setResolvedX(childX);
                child.setResolvedY(childY);
                cursor += child.getResolvedWidth();
            } else {
                float childY = stackY + cursor;
                AnchorPoint parentAnchor = child.getAnchorFrom();
                AnchorPoint elementAnchor = child.getAnchorTo();
                float crossBase = parentAnchor.getX() * stackW - elementAnchor.getX() * child.getResolvedWidth();
                float childX = stackX + crossBase;

                child.setResolvedX(childX);
                child.setResolvedY(childY);
                cursor += child.getResolvedHeight();
            }

            if (isStackPanel(child)) {
                resolveStackChildren(child);
            } else if (child instanceof UIElementScroll scroll) {
                float contentAbsX = scroll.getResolvedX();
                float contentAbsY = scroll.getResolvedY() - scroll.getScrollOffset();
                for (UIElement grandchild : scroll.getChildren()) {
                    resolvePosition(grandchild, contentAbsX, contentAbsY);
                }
            } else {
                for (UIElement grandchild : child.getChildren()) {
                    resolvePosition(grandchild, child.getResolvedX(), child.getResolvedY());
                }
            }
        }
    }

}
