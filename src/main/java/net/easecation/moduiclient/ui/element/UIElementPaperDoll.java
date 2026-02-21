package net.easecation.moduiclient.ui.element;

import com.google.gson.JsonObject;
import net.easecation.moduiclient.ModUIClient;
import net.easecation.moduiclient.entity.EntityMappingStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/**
 * Paper doll element — renders a 3D entity model inside the UI.
 *
 * Supports binding to a world entity via entity_id, with optional
 * auto-rotation and scale controls.
 *
 * Reference: ECBaseUI.py paperDoll handling, ModUIPaperDoll.java (server-side data)
 */
public class UIElementPaperDoll extends UIElement {

    // Doll configuration (parsed from JSON "doll" object)
    private long entityId = -1;
    private String rotation = null;   // "auto" for auto-rotate, null for static
    private float scale = 1.0f;
    private float initRotY = 0f;

    // Auto-rotation state
    private float autoRotAngle = 0f;

    public UIElementPaperDoll(String name, String type) {
        super(name, type);
    }

    @Override
    public void initFromJson(JsonObject json) {
        super.initFromJson(json);

        if (json.has("doll") && json.get("doll").isJsonObject()) {
            parseDoll(json.getAsJsonObject("doll"));
        }
    }

    private void parseDoll(JsonObject doll) {
        if (doll.has("entity_id")) {
            String idStr = doll.get("entity_id").getAsString();
            try {
                entityId = Long.parseLong(idStr);
            } catch (NumberFormatException e) {
                ModUIClient.LOGGER.warn("[PaperDoll] Invalid entity_id: {}", idStr);
                entityId = -1;
            }
        }

        if (doll.has("rotation")) {
            rotation = doll.get("rotation").getAsString();
        }

        if (doll.has("scale")) {
            scale = doll.get("scale").getAsFloat();
        }

        if (doll.has("init_rot_y")) {
            initRotY = doll.get("init_rot_y").getAsFloat();
        }

        ModUIClient.LOGGER.info("[PaperDoll] Parsed doll: entityId={}, rotation={}, scale={}, initRotY={}",
                entityId, rotation, scale, initRotY);
    }

    /**
     * Update doll configuration from a RenderEntity command value.
     */
    public void setDollFromJson(JsonObject doll) {
        parseDoll(doll);
        // Reset auto-rotation when entity changes
        autoRotAngle = 0f;
    }

    @Override
    public void render(DrawContext context, float tickDelta) {
        if (!isVisible() || getAlpha() <= 0) return;

        int x1 = (int) getResolvedX();
        int y1 = (int) getResolvedY();
        int w = (int) getResolvedWidth();
        int h = (int) getResolvedHeight();
        if (w <= 0 || h <= 0) return;

        int x2 = x1 + w;
        int y2 = y1 + h;

        if (entityId < 0) return;

        // Translate Bedrock runtime entity ID → Java entity ID
        int javaId = EntityMappingStore.getInstance().getJavaEntityId(entityId);
        if (javaId < 0) return; // Mapping not yet received

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        Entity entity = client.world.getEntityById(javaId);
        if (!(entity instanceof LivingEntity living)) return;

        // Calculate entity scale based on element size
        int size = (int) (Math.min(w, h) * 0.8f * scale);
        if (size <= 0) size = 1;

        float centerX = (x1 + x2) / 2.0f;
        float centerY = (y1 + y2) / 2.0f;

        float mouseX, mouseY;
        if ("auto".equals(rotation)) {
            // Auto-rotate: sweep mouse position in a circle
            autoRotAngle += tickDelta * 2f;
            mouseX = centerX + (float) Math.sin(Math.toRadians(autoRotAngle)) * 40f;
            mouseY = centerY - 10f; // Slightly above center for natural look
        } else {
            // Static: apply init_rot_y offset
            // init_rot_y is in degrees; convert to fake mouse offset
            // tan(angle) * 40 gives the mouse offset that produces that rotation
            mouseX = centerX + (float) Math.tan(Math.toRadians(initRotY)) * 40f;
            mouseY = centerY - 10f;
        }

        InventoryScreen.drawEntity(context, x1, y1, x2, y2, size, 0f, mouseX, mouseY, living);
    }

    // --- Getters ---

    public long getEntityId() { return entityId; }
    public String getRotation() { return rotation; }
    public float getScale() { return scale; }
    public float getInitRotY() { return initRotY; }

    // --- Setters for commands ---

    public void setEntityId(long entityId) { this.entityId = entityId; }

    public void setRotation(String rotation) {
        this.rotation = rotation;
        if (!"auto".equals(rotation)) {
            autoRotAngle = 0f;
        }
    }

    public void setScale(float scale) { this.scale = scale; }
    public void setInitRotY(float initRotY) { this.initRotY = initRotY; }
}
