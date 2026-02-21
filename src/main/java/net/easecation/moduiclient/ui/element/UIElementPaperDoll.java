package net.easecation.moduiclient.ui.element;

import com.google.gson.JsonObject;
import net.easecation.moduiclient.ModUIClient;
import net.easecation.moduiclient.entity.EntityMappingStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Paper doll element — renders a 3D entity model inside the UI.
 *
 * Supports binding to a world entity via entity_id, with optional
 * auto-rotation, scale, and interactive mouse drag rotation.
 *
 * Uses DrawContext.addEntity() directly (bypassing InventoryScreen.drawEntity)
 * to achieve full 360-degree rotation control.
 */
public class UIElementPaperDoll extends UIElement {

    // Doll configuration (parsed from JSON "doll" object)
    private long entityId = -1;
    private String rotation = null;   // "auto" for auto-rotate, null for static
    private float scale = 1.0f;
    private float initRotY = 0f;

    // Auto-rotation state
    private float autoRotAngle = 0f;

    // Mouse drag state
    private boolean dragging = false;
    private float dragAngleOffset = 0f;
    private static final float DRAG_SENSITIVITY = 0.8f;
    private static final float EASE_BACK_SPEED = 0.1f;

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
        autoRotAngle = 0f;
    }

    // --- Mouse drag API (called by ModUIStackScreen) ---

    public void beginDrag() {
        dragging = true;
    }

    public void processDrag(double deltaX) {
        dragAngleOffset -= (float) deltaX * DRAG_SENSITIVITY;
    }

    public void endDrag() {
        dragging = false;
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
        if (javaId < 0) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        Entity entity = client.world.getEntityById(javaId);
        if (!(entity instanceof LivingEntity living)) return;

        // Ease drag offset back to 0 when not dragging
        if (!dragging && dragAngleOffset != 0) {
            dragAngleOffset *= (1.0f - EASE_BACK_SPEED);
            if (Math.abs(dragAngleOffset) < 0.5f) {
                dragAngleOffset = 0f;
            }
        }

        // Calculate final rotation angle
        float baseAngle;
        if ("auto".equals(rotation)) {
            if (!dragging) {
                autoRotAngle += tickDelta * 0.8f;
            }
            baseAngle = autoRotAngle;
        } else {
            baseAngle = initRotY;
        }
        float displayAngle = 180f + baseAngle + dragAngleOffset;

        // Calculate entity scale
        int size = (int) (Math.min(w, h) * 0.8f * scale);
        if (size <= 0) size = 1;

        renderEntityRotated(context, living, x1, y1, x2, y2, size, displayAngle);
    }

    /**
     * Render entity with full 360-degree rotation control.
     * Bypasses InventoryScreen.drawEntity which limits rotation to ~60 degrees.
     */
    @SuppressWarnings("unchecked")
    private static void renderEntityRotated(DrawContext context, LivingEntity entity,
                                            int x1, int y1, int x2, int y2, int size, float bodyYawDeg) {
        MinecraftClient client = MinecraftClient.getInstance();
        EntityRenderManager renderManager = client.getEntityRenderDispatcher();
        EntityRenderer<Entity, EntityRenderState> renderer =
                (EntityRenderer<Entity, EntityRenderState>) renderManager.getRenderer(entity);
        EntityRenderState renderState = renderer.getAndUpdateRenderState(entity, 1.0f);
        renderState.light = 15728880; // Full brightness
        renderState.shadowPieces.clear();
        renderState.outlineColor = 0;

        if (renderState instanceof LivingEntityRenderState livingState) {
            livingState.bodyYaw = bodyYawDeg;
            livingState.relativeHeadYaw = 0;
            livingState.pitch = 0;
            livingState.width /= livingState.baseScale;
            livingState.height /= livingState.baseScale;
            livingState.baseScale = 1.0f;
        }

        // Pose quaternion: Z-flip (π) to render upright
        Quaternionf poseQuat = new Quaternionf().rotateZ((float) Math.PI);
        // No camera tilt
        Quaternionf cameraQuat = new Quaternionf();

        Vector3f offset = new Vector3f(0, renderState.height / 2f, 0);
        context.addEntity(renderState, (float) size, offset, poseQuat, cameraQuat, x1, y1, x2, y2);
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
