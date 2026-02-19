package net.easecation.moduiclient.ui;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.easecation.moduiclient.ModUIClient;
import net.easecation.moduiclient.ui.animation.AnimationManager;
import net.easecation.moduiclient.ui.element.UIElement;
import net.easecation.moduiclient.ui.element.UIElementDraggable;
import net.easecation.moduiclient.ui.layout.LayoutEngine;

import java.util.HashMap;
import java.util.Map;

/**
 * Manages a tree of UI elements with name-based and path-based lookup.
 * The root element represents the screen/HUD area.
 *
 * Mirrors ECBaseUI.py's pathMap which maps element NAME → full path.
 * parentNode values in JSON are element names (not paths), except "/" and "&/" for root.
 */
public class UITree {

    private static final Gson GSON = new Gson();

    private final UIElement root;
    /** Maps full path → element (for path-based lookup) */
    private final Map<String, UIElement> pathMap = new HashMap<>();
    /** Maps element name → element (for parentNode resolution, mirrors ECBaseUI.pathMap) */
    private final Map<String, UIElement> nameMap = new HashMap<>();
    private boolean layoutDirty = true;
    private final AnimationManager animationManager;

    public UITree(float screenWidth, float screenHeight) {
        this.root = new UIElement("root", "panel");
        this.animationManager = new AnimationManager(this);
        this.root.setFullPath("");
        this.root.setResolvedWidth(screenWidth);
        this.root.setResolvedHeight(screenHeight);
        this.root.setResolvedX(0);
        this.root.setResolvedY(0);
        pathMap.put("/", root);
    }

    /**
     * Initialize the tree from a JSON array of element definitions.
     * Format: [ { "type": "image", "name": "bg", "parentNode": "/", ... }, ... ]
     */
    public void initFromJson(String jsonStr) {
        JsonArray elements = GSON.fromJson(jsonStr, JsonArray.class);
        if (elements == null) return;

        for (JsonElement elem : elements) {
            if (!elem.isJsonObject()) continue;
            JsonObject obj = elem.getAsJsonObject();
            try {
                addElementFromJson(obj);
            } catch (Exception e) {
                String name = obj.has("name") ? obj.get("name").getAsString() : "?";
                ModUIClient.LOGGER.warn("[UITree] Failed to add element '{}': {}", name, e.getMessage());
            }
        }

        markLayoutDirty();
    }

    /**
     * Add a single element from JSON definition.
     * parentNode resolution follows ECBaseUI.py logic:
     *   "/" or "&/" → root
     *   other → lookup by element name in nameMap
     */
    public UIElement addElementFromJson(JsonObject json) {
        String parentNode = json.has("parentNode") ? json.get("parentNode").getAsString() : "/";

        // Resolve parent: "/" and "&/" map to root, otherwise lookup by name
        UIElement parent;
        if ("/".equals(parentNode) || "&/".equals(parentNode)) {
            parent = root;
        } else {
            parent = nameMap.get(parentNode);
            if (parent == null) {
                ModUIClient.LOGGER.warn("[UITree] Parent not found by name: {}", parentNode);
                parent = root;
            }
        }

        UIElement element = UIElement.fromJson(json);
        parent.addChild(element);

        ModUIClient.LOGGER.info("[UITree] addElement: name='{}', type='{}', parent='{}' (type={}), path='{}', size=[{}, {}], pos=[{}, {}], anchorFrom={}, anchorTo={}",
                element.getName(), element.getType(), parent.getName(), parent.getType(),
                element.getFullPath(), element.getSizeX(), element.getSizeY(),
                element.getPositionX(), element.getPositionY(),
                element.getAnchorFrom(), element.getAnchorTo());

        // Register in both maps
        String name = element.getName();
        if (name != null && !name.isEmpty()) {
            nameMap.put(name, element);
        }
        String fullPath = element.getFullPath();
        if (fullPath != null) {
            pathMap.put(fullPath, element);
        }

        // Apply initial position for draggable elements (needs tree to be built)
        if (element instanceof UIElementDraggable draggable) {
            draggable.applyInitialPosition();
        }

        return element;
    }

    /**
     * Remove an element by name (as used by commands).
     */
    public void removeElementByName(String name) {
        UIElement element = nameMap.get(name);
        if (element == null || element == root) return;
        removeFromMaps(element);
        if (element.getParent() != null) {
            element.getParent().removeChild(element);
        }
    }

    /**
     * Remove an element by path.
     */
    public void removeElement(String path) {
        UIElement element = findByPath(path);
        if (element == null || element == root) return;
        removeFromMaps(element);
        if (element.getParent() != null) {
            element.getParent().removeChild(element);
        }
    }

    private void removeFromMaps(UIElement element) {
        if (element.getName() != null) {
            nameMap.remove(element.getName());
            animationManager.removeAnimationsForElement(element.getName());
        }
        if (element.getFullPath() != null) {
            pathMap.remove(element.getFullPath());
        }
        for (UIElement child : element.getChildren()) {
            removeFromMaps(child);
        }
    }

    /**
     * Find element by name (for command dispatch).
     */
    public UIElement findByName(String name) {
        return nameMap.get(name);
    }

    /**
     * Find element by path (e.g. "/bg_panel/title").
     */
    public UIElement findByPath(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) return root;
        // Try direct lookup first
        UIElement cached = pathMap.get(path);
        if (cached != null) return cached;
        // Fall back to tree traversal
        return root.findByPath(path);
    }

    /**
     * Update layout if dirty.
     */
    public void updateLayout(float screenWidth, float screenHeight) {
        // Detect screen size change and mark layout dirty
        if (screenWidth != root.getResolvedWidth() || screenHeight != root.getResolvedHeight()) {
            root.setResolvedWidth(screenWidth);
            root.setResolvedHeight(screenHeight);
            layoutDirty = true;
        }

        if (layoutDirty) {
            LayoutEngine.resolveTree(root);
            layoutDirty = false;
        }
    }

    public void markLayoutDirty() {
        layoutDirty = true;
    }

    /**
     * Tick all active animations. Must be called before updateLayout() each frame.
     */
    public void tickAnimations() {
        if (animationManager.hasAnimations()) {
            if (animationManager.tick()) {
                markLayoutDirty();
            }
        }
    }

    public AnimationManager getAnimationManager() { return animationManager; }

    public UIElement getRoot() { return root; }
}
