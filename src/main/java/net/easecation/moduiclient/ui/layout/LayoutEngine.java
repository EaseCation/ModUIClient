package net.easecation.moduiclient.ui.layout;

import net.easecation.moduiclient.ui.element.UIElement;

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
        for (UIElement child : element.getChildren()) {
            resolveSize(child);
        }

        float parentW = element.getParent() != null ? element.getParent().getResolvedWidth() : element.getResolvedWidth();
        float parentH = element.getParent() != null ? element.getParent().getResolvedHeight() : element.getResolvedHeight();

        float childrenW = 0, childrenH = 0;
        float maxChildW = 0, maxChildH = 0;
        for (UIElement child : element.getChildren()) {
            childrenW += child.getResolvedWidth();
            childrenH += child.getResolvedHeight();
            maxChildW = Math.max(maxChildW, child.getResolvedWidth());
            maxChildH = Math.max(maxChildH, child.getResolvedHeight());
        }

        float maxSiblingW = 0, maxSiblingH = 0;
        if (element.getParent() != null) {
            for (UIElement sibling : element.getParent().getChildren()) {
                if (sibling != element) {
                    maxSiblingW = Math.max(maxSiblingW, sibling.getResolvedWidth());
                    maxSiblingH = Math.max(maxSiblingH, sibling.getResolvedHeight());
                }
            }
        }

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

    private static void resolvePosition(UIElement element, float parentAbsX, float parentAbsY) {
        if (element.getParent() != null) {
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
        }

        if (isStackPanel(element)) {
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
            } else {
                for (UIElement grandchild : child.getChildren()) {
                    resolvePosition(grandchild, child.getResolvedX(), child.getResolvedY());
                }
            }
        }
    }

}
