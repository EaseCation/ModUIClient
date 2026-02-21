package net.easecation.moduiclient.render;

import net.easecation.moduiclient.ModUIClient;
import net.easecation.moduiclient.ui.UIManager;
import net.easecation.moduiclient.ui.UITree;
import net.easecation.moduiclient.ui.element.UIElement;
import net.easecation.moduiclient.ui.element.UIElementButton;
import net.easecation.moduiclient.ui.element.UIElementDraggable;
import net.easecation.moduiclient.ui.element.UIElementScroll;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Screen implementation for Stack UI (modal dialogs/menus).
 * Handles button clicks, scroll wheel, scrollbar drag, and element drag.
 */
public class ModUIStackScreen extends Screen {

    private final UITree tree;

    // Scrollbar drag state
    private UIElementScroll scrollbarDragTarget = null;
    private double scrollbarDragLastY = 0;

    // Element drag state
    private UIElementDraggable activeDraggable = null;

    public ModUIStackScreen(UITree tree) {
        super(Text.literal("ModUI"));
        this.tree = tree;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Dark background
        context.fill(0, 0, this.width, this.height, 0x80000000);

        if (tree == null) return;

        // Tick animations and update layout
        tree.tickAnimations();
        tree.updateLayout(this.width, this.height);

        // Update button hover states
        updateButtonHoverStates(tree.getRoot(), mouseX, mouseY);

        // Update scrollbar thumb hover states
        updateScrollThumbHover(tree.getRoot(), mouseX, mouseY);

        tree.getRoot().renderTree(context, delta);
    }

    // --- Mouse: Click ---

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (tree == null) return super.mouseClicked(click, doubled);
        if (click.button() == 0) { // Left click
            double mx = click.x(), my = click.y();

            // 1. Check scrollbar track/thumb click
            UIElementScroll scroll = findScroll(tree.getRoot(), mx, my);
            if (scroll != null) {
                if (scroll.isPointOnThumb((float) mx, (float) my)) {
                    // Start scrollbar drag
                    scrollbarDragTarget = scroll;
                    scrollbarDragLastY = my;
                    scroll.setScrollbarDragging(true);
                    return true;
                } else if (scroll.isPointOnTrack((float) mx, (float) my)) {
                    // Page scroll on track click
                    scroll.handleTrackClick((float) my);
                    scroll.notifyScrollbarActivity();
                    tree.markLayoutDirty();
                    return true;
                }
            }

            // 2. Check button click (before draggable, so buttons inside draggable areas work)
            UIElementButton clickedButton = findButton(tree.getRoot(), mx, my);
            if (clickedButton != null) {
                clickedButton.setPressed(true);
                return true;
            }

            // 3. Check draggable element
            UIElementDraggable draggable = findDraggable(tree.getRoot(), mx, my);
            if (draggable != null) {
                activeDraggable = draggable;
                draggable.beginDrag(mx, my);
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    // --- Mouse: Release ---

    @Override
    public boolean mouseReleased(Click click) {
        if (tree == null) return super.mouseReleased(click);
        if (click.button() == 0) {
            // End scrollbar drag
            if (scrollbarDragTarget != null) {
                scrollbarDragTarget.setScrollbarDragging(false);
                scrollbarDragTarget = null;
                return true;
            }

            // End element drag
            if (activeDraggable != null) {
                activeDraggable.endDrag();
                activeDraggable = null;
                return true;
            }

            // Button click
            UIElementButton clickedButton = findButton(tree.getRoot(), click.x(), click.y());
            if (clickedButton != null) {
                String fullPath = clickedButton.getFullPath();
                String buttonName = clickedButton.getName();
                ModUIClient.LOGGER.info("[StackScreen] Button clicked: path={}, name={}", fullPath, buttonName);
                UIManager.getInstance().sendStackButtonClick(
                        fullPath != null ? fullPath : buttonName,
                        buttonName
                );
                if (clickedButton.isClientClose()) {
                    ModUIClient.LOGGER.info("[StackScreen] clientClose button — closing stack");
                    releaseAllButtons(tree.getRoot());
                    this.close();
                    return super.mouseReleased(click);
                }
            }
            releaseAllButtons(tree.getRoot());
        }
        return super.mouseReleased(click);
    }

    // --- Mouse: Drag ---

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (tree == null) return super.mouseDragged(click, deltaX, deltaY);
        if (click.button() == 0) {
            double mouseX = click.x(), mouseY = click.y();

            // Scrollbar thumb drag
            if (scrollbarDragTarget != null) {
                double dy = mouseY - scrollbarDragLastY;
                float scrollDelta = scrollbarDragTarget.trackDeltaToScrollDelta((float) dy);
                scrollbarDragTarget.setScrollOffset(scrollbarDragTarget.getScrollOffset() + scrollDelta);
                scrollbarDragTarget.notifyScrollbarActivity();
                scrollbarDragLastY = mouseY;
                tree.markLayoutDirty();
                return true;
            }

            // Element drag
            if (activeDraggable != null) {
                if (activeDraggable.processDrag(mouseX, mouseY)) {
                    tree.markLayoutDirty();
                }
                return true;
            }
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    // --- Mouse: Scroll ---

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (tree == null) return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
        UIElementScroll scroll = findDeepestScroll(tree.getRoot(), mouseX, mouseY);
        if (scroll != null) {
            float step = 20f;
            scroll.setScrollOffset(scroll.getScrollOffset() - (float) verticalAmount * step);
            scroll.notifyScrollbarActivity();
            tree.markLayoutDirty();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    // --- Close ---

    @Override
    public void close() {
        // User pressed ESC → send global_close to server
        UIManager.getInstance().handleStackCloseByUser();
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // --- Element finding ---

    private void updateButtonHoverStates(UIElement element, double mouseX, double mouseY) {
        if (element instanceof UIElementButton btn) {
            btn.updateHoverState(mouseX, mouseY);
        }
        // Skip children of scroll elements if point is outside viewport
        if (element instanceof UIElementScroll && !element.containsPoint((float) mouseX, (float) mouseY)) {
            return;
        }
        for (UIElement child : element.getChildren()) {
            updateButtonHoverStates(child, mouseX, mouseY);
        }
    }

    private void updateScrollThumbHover(UIElement element, double mouseX, double mouseY) {
        if (element instanceof UIElementScroll scroll) {
            scroll.setThumbHovered(scroll.isPointOnThumb((float) mouseX, (float) mouseY));
            scroll.setMouseNearScrollbar(scroll.isPointNearScrollbar((float) mouseX, (float) mouseY));
        }
        for (UIElement child : element.getChildren()) {
            updateScrollThumbHover(child, mouseX, mouseY);
        }
    }

    /**
     * Find the deepest scroll element that contains the mouse point (for wheel scroll).
     */
    private UIElementScroll findDeepestScroll(UIElement element, double mouseX, double mouseY) {
        // Skip children of scroll elements if point is outside viewport
        if (element instanceof UIElementScroll && !element.containsPoint((float) mouseX, (float) mouseY)) {
            return null;
        }
        // Search children first (deeper scroll takes priority)
        for (int i = element.getChildren().size() - 1; i >= 0; i--) {
            UIElement child = element.getChildren().get(i);
            UIElementScroll found = findDeepestScroll(child, mouseX, mouseY);
            if (found != null) return found;
        }
        // Check self
        if (element instanceof UIElementScroll scroll
                && scroll.isVisible()
                && scroll.containsPoint((float) mouseX, (float) mouseY)
                && scroll.getMaxScrollOffset() > 0) {
            return scroll;
        }
        return null;
    }

    /**
     * Find a scroll element whose scrollbar is at the given point.
     */
    private UIElementScroll findScroll(UIElement element, double mouseX, double mouseY) {
        // Skip children of scroll elements if point is outside viewport
        if (element instanceof UIElementScroll && !element.containsPoint((float) mouseX, (float) mouseY)) {
            return null;
        }
        // Search children first (deeper scrollbar takes priority)
        for (int i = element.getChildren().size() - 1; i >= 0; i--) {
            UIElement child = element.getChildren().get(i);
            UIElementScroll found = findScroll(child, mouseX, mouseY);
            if (found != null) return found;
        }
        // Check self
        if (element instanceof UIElementScroll scroll
                && scroll.isVisible()
                && scroll.isScrollbarInteractable()
                && scroll.containsPoint((float) mouseX, (float) mouseY)) {
            float[] track = scroll.getTrackBounds();
            if (track != null
                    && mouseX >= track[0] && mouseX < track[0] + track[2]
                    && mouseY >= track[1] && mouseY < track[1] + track[3]) {
                return scroll;
            }
        }
        return null;
    }

    /**
     * Find the deepest draggable element containing the mouse point.
     */
    private UIElementDraggable findDraggable(UIElement element, double mouseX, double mouseY) {
        // Skip children of scroll elements if point is outside viewport
        if (element instanceof UIElementScroll && !element.containsPoint((float) mouseX, (float) mouseY)) {
            return null;
        }
        // Search children first (deeper element takes priority)
        for (int i = element.getChildren().size() - 1; i >= 0; i--) {
            UIElement child = element.getChildren().get(i);
            UIElementDraggable found = findDraggable(child, mouseX, mouseY);
            if (found != null) return found;
        }
        if (element instanceof UIElementDraggable drag
                && drag.isVisible()
                && drag.containsPoint((float) mouseX, (float) mouseY)) {
            return drag;
        }
        return null;
    }

    private UIElementButton findButton(UIElement element, double mouseX, double mouseY) {
        // Skip children of scroll elements if point is outside viewport
        if (element instanceof UIElementScroll && !element.containsPoint((float) mouseX, (float) mouseY)) {
            return null;
        }
        // Check children first (higher layer elements should be checked first)
        for (int i = element.getChildren().size() - 1; i >= 0; i--) {
            UIElement child = element.getChildren().get(i);
            UIElementButton found = findButton(child, mouseX, mouseY);
            if (found != null) return found;
        }

        if (element instanceof UIElementButton btn && element.isVisible()
                && element.containsPoint((float) mouseX, (float) mouseY)) {
            return btn;
        }
        return null;
    }

    private void releaseAllButtons(UIElement element) {
        if (element instanceof UIElementButton btn) {
            btn.setPressed(false);
        }
        for (UIElement child : element.getChildren()) {
            releaseAllButtons(child);
        }
    }
}
