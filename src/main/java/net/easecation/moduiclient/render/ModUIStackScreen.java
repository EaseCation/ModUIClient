package net.easecation.moduiclient.render;

import net.easecation.moduiclient.ui.UIManager;
import net.easecation.moduiclient.ui.UITree;
import net.easecation.moduiclient.ui.element.UIElement;
import net.easecation.moduiclient.ui.element.UIElementButton;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * Screen implementation for Stack UI (modal dialogs/menus).
 */
public class ModUIStackScreen extends Screen {

    private final UITree tree;

    public ModUIStackScreen(UITree tree) {
        super(Text.literal("ModUI"));
        this.tree = tree;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Dark background
        context.fill(0, 0, this.width, this.height, 0x80000000);

        // Update layout and render
        tree.updateLayout(this.width, this.height);

        // Update button hover states
        updateButtonHoverStates(tree.getRoot(), mouseX, mouseY);

        tree.getRoot().renderTree(context, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) { // Left click
            UIElementButton clickedButton = findButton(tree.getRoot(), click.x(), click.y());
            if (clickedButton != null) {
                clickedButton.setPressed(true);
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0) {
            // Find button under cursor and send click event
            UIElementButton clickedButton = findButton(tree.getRoot(), click.x(), click.y());
            if (clickedButton != null) {
                String fullPath = clickedButton.getFullPath();
                String buttonName = clickedButton.getName();
                UIManager.getInstance().sendStackButtonClick(
                        fullPath != null ? fullPath : buttonName,
                        buttonName
                );
            }
            releaseAllButtons(tree.getRoot());
        }
        return super.mouseReleased(click);
    }

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

    private void updateButtonHoverStates(UIElement element, double mouseX, double mouseY) {
        if (element instanceof UIElementButton btn) {
            btn.updateHoverState(mouseX, mouseY);
        }
        for (UIElement child : element.getChildren()) {
            updateButtonHoverStates(child, mouseX, mouseY);
        }
    }

    private UIElementButton findButton(UIElement element, double mouseX, double mouseY) {
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
