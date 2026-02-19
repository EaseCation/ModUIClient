package net.easecation.moduiclient.ui.animation;

import net.easecation.moduiclient.ui.UITree;
import net.easecation.moduiclient.ui.element.UIElement;

import java.util.*;

/**
 * Manages all active animations for a single UITree (HUD or Stack).
 * Each UITree holds its own AnimationManager instance.
 *
 * Animations are stored by element name and ticked every frame.
 * Finished animations are removed automatically.
 * When an element is destroyed, its animations are cleaned up.
 *
 * All operations run on the main render thread — no synchronization needed.
 *
 * Reference: ECBaseUI.py animationControllers dict + on_frame_tick()
 */
public class AnimationManager {

    /** elementName → list of active animations for that element. */
    private final Map<String, List<UIAnimation>> animations = new HashMap<>();

    /** Associated UITree for element lookup. */
    private final UITree tree;

    public AnimationManager(UITree tree) {
        this.tree = tree;
    }

    /**
     * Add animations for an element (append semantics — does not replace existing).
     * Corresponds to the AddAnimations command.
     */
    public void addAnimations(String elementName, List<UIAnimation> newAnimations) {
        animations.computeIfAbsent(elementName, k -> new ArrayList<>()).addAll(newAnimations);
    }

    /**
     * Tick all active animations.
     * Must be called once per frame, before layout update.
     *
     * @return true if any animation modified position/size (layout needs refresh)
     */
    public boolean tick() {
        if (animations.isEmpty()) return false;

        boolean needsLayout = false;
        long now = System.nanoTime();

        Iterator<Map.Entry<String, List<UIAnimation>>> entryIter = animations.entrySet().iterator();
        while (entryIter.hasNext()) {
            Map.Entry<String, List<UIAnimation>> entry = entryIter.next();
            String elementName = entry.getKey();
            List<UIAnimation> animList = entry.getValue();

            // Check if element still exists — if destroyed, remove all its animations
            UIElement element = tree.findByName(elementName);
            if (element == null) {
                entryIter.remove();
                continue;
            }

            // Tick each animation
            Iterator<UIAnimation> animIter = animList.iterator();
            while (animIter.hasNext()) {
                UIAnimation anim = animIter.next();
                UIAnimation.TickResult result = anim.tick(element, now);
                switch (result) {
                    case LAYOUT -> needsLayout = true;
                    case FINISHED -> {
                        animIter.remove();
                        // A finished position/size animation also applied its final value
                        if (!anim.isFinished()) break; // shouldn't happen, but guard
                    }
                    case NONE -> {}
                }
            }

            // Remove entry if all animations for this element are done
            if (animList.isEmpty()) {
                entryIter.remove();
            }
        }

        return needsLayout;
    }

    /**
     * Remove all animations for a specific element.
     * Called when the element is removed from the tree.
     */
    public void removeAnimationsForElement(String elementName) {
        if (elementName != null) {
            animations.remove(elementName);
        }
    }

    /** Clear all animations (called on tree reset). */
    public void clear() {
        animations.clear();
    }

    /** Whether there are any active animations. */
    public boolean hasAnimations() {
        return !animations.isEmpty();
    }
}
