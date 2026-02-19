package net.easecation.moduiclient.ui.command;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.easecation.moduiclient.ModUIClient;
import net.easecation.moduiclient.ui.UIManager;
import net.easecation.moduiclient.ui.UITree;
import net.easecation.moduiclient.ui.element.UIElement;
import net.easecation.moduiclient.ui.element.UIElementButton;
import net.easecation.moduiclient.ui.element.UIElementDraggable;
import net.easecation.moduiclient.ui.element.UIElementImage;
import net.easecation.moduiclient.ui.element.UIElementScroll;
import net.easecation.moduiclient.ui.element.UIElementText;
import net.easecation.moduiclient.ui.animation.UIAnimation;
import net.easecation.moduiclient.ui.layout.AnchorPoint;
import net.easecation.moduiclient.ui.layout.SizeExpression;

import java.util.ArrayList;
import java.util.List;


/**
 * Processes incremental UI commands from RequestControlNodeEvent.
 *
 * Server command format (MsgPack map):
 *   {"command": "SetVisible", "bodyName": "element_name", "value": ...}
 *
 * bodyName is an element NAME (not path), matching ECBaseUI.py's pathMap key.
 *
 * Reference: ECBaseUI.py handle_command()
 */
public class UICommandProcessor {

    /**
     * Process a batch of commands.
     * Each entry is a JsonObject with {command, bodyName, value} fields.
     * @return true if layout needs refresh (AddElement/RemoveElement occurred)
     */
    public static boolean processBatch(UITree tree, JsonArray commandList) {
        boolean needsLayout = false;

        for (JsonElement cmdElem : commandList) {
            try {
                String commandName;
                String bodyName;
                JsonElement value;

                if (cmdElem.isJsonObject()) {
                    // Server format: {"command": "SetVisible", "bodyName": "bg_panel", "value": ...}
                    JsonObject cmdObj = cmdElem.getAsJsonObject();
                    commandName = getStringField(cmdObj, "command");
                    bodyName = getStringField(cmdObj, "bodyName");
                    value = getField(cmdObj, "value");
                } else if (cmdElem.isJsonArray()) {
                    // Fallback array format: ["SetVisible", "bg_panel", value]
                    JsonArray cmd = cmdElem.getAsJsonArray();
                    if (cmd.isEmpty()) continue;
                    commandName = cmd.get(0).getAsString();
                    bodyName = cmd.size() > 1 ? cmd.get(1).getAsString() : null;
                    value = cmd.size() > 2 ? cmd.get(2) : null;
                } else {
                    continue;
                }

                if (commandName == null) continue;

                boolean layoutChanged = processCommand(tree, commandName, bodyName, value);
                if (layoutChanged) needsLayout = true;
            } catch (Exception e) {
                ModUIClient.LOGGER.warn("[UICommand] Failed to process command: {}", cmdElem, e);
            }
        }

        if (needsLayout) {
            tree.markLayoutDirty();
        }

        return needsLayout;
    }

    private static boolean processCommand(UITree tree, String commandName, String bodyName, JsonElement value) {
        return switch (commandName) {
            // --- Visibility ---
            case "SetVisible" -> {
                UIElement el = tree.findByName(bodyName);
                if (el != null && value != null) el.setVisible(value.getAsBoolean());
                yield false;
            }

            // --- Text ---
            case "SetText" -> {
                UIElement el = tree.findByName(bodyName);
                if (el instanceof UIElementText text && value != null) {
                    text.setText(asString(value));
                    tree.markLayoutDirty();
                }
                yield false;
            }
            case "SetTextColor" -> {
                UIElement el = tree.findByName(bodyName);
                if (el instanceof UIElementText text && value != null) {
                    JsonArray color = value.getAsJsonArray();
                    float r = color.get(0).getAsFloat();
                    float g = color.get(1).getAsFloat();
                    float b = color.get(2).getAsFloat();
                    float a = color.size() > 3 ? color.get(3).getAsFloat() : 1f;
                    text.setTextColor(r, g, b, a);
                }
                yield false;
            }
            case "SetFontSize" -> {
                UIElement el = tree.findByName(bodyName);
                if (el instanceof UIElementText text && value != null) text.setFontSize(value.getAsFloat());
                yield false;
            }
            case "SetTextAlignment" -> {
                UIElement el = tree.findByName(bodyName);
                if (el instanceof UIElementText text && value != null) text.setTextAlignment(asString(value));
                yield false;
            }

            // --- Position/Size ---
            case "SetPosition" -> {
                UIElement el = tree.findByName(bodyName);
                if (el != null && value != null) {
                    JsonArray pos = value.getAsJsonArray();
                    el.setPositionX(SizeExpression.parse(asString(pos.get(0))));
                    el.setPositionY(SizeExpression.parse(asString(pos.get(1))));
                    tree.markLayoutDirty();
                }
                yield false;
            }
            case "SetSize" -> {
                UIElement el = tree.findByName(bodyName);
                if (el != null && value != null) {
                    JsonArray size = value.getAsJsonArray();
                    el.setSizeX(SizeExpression.parse(asString(size.get(0))));
                    el.setSizeY(SizeExpression.parse(asString(size.get(1))));
                    tree.markLayoutDirty();
                }
                yield false;
            }
            case "SetMaxSize" -> {
                UIElement el = tree.findByName(bodyName);
                if (el != null && value != null) {
                    JsonArray maxSize = value.getAsJsonArray();
                    el.setMaxWidth(maxSize.get(0).getAsFloat());
                    el.setMaxHeight(maxSize.get(1).getAsFloat());
                    tree.markLayoutDirty();
                }
                yield false;
            }
            case "SetMinSize" -> {
                UIElement el = tree.findByName(bodyName);
                if (el != null && value != null) {
                    JsonArray minSize = value.getAsJsonArray();
                    el.setMinWidth(minSize.get(0).getAsFloat());
                    el.setMinHeight(minSize.get(1).getAsFloat());
                    tree.markLayoutDirty();
                }
                yield false;
            }

            // --- Alpha ---
            case "SetAlpha" -> {
                UIElement el = tree.findByName(bodyName);
                if (el != null && value != null) el.setAlpha(value.getAsFloat());
                yield false;
            }

            // --- Layer ---
            case "SetLayer" -> {
                UIElement el = tree.findByName(bodyName);
                if (el != null && value != null) el.setLayer(value.getAsInt());
                yield false;
            }

            // --- Image/Sprite ---
            case "SetSprite" -> {
                UIElement el = tree.findByName(bodyName);
                if (el instanceof UIElementImage img && value != null) img.setTexturePath(asString(value));
                yield false;
            }
            case "SetSpriteColor" -> {
                UIElement el = tree.findByName(bodyName);
                if (el instanceof UIElementImage img && value != null) {
                    JsonArray color = value.getAsJsonArray();
                    img.setColor(color.get(0).getAsFloat(), color.get(1).getAsFloat(), color.get(2).getAsFloat());
                }
                yield false;
            }
            case "SetUV" -> {
                UIElement el = tree.findByName(bodyName);
                if (el instanceof UIElementImage img && value != null) {
                    JsonArray uv = value.getAsJsonArray();
                    img.setUV(uv.get(0).getAsFloat(), uv.get(1).getAsFloat());
                }
                yield false;
            }
            case "SetUVSize" -> {
                UIElement el = tree.findByName(bodyName);
                if (el instanceof UIElementImage img && value != null) {
                    JsonArray uvSize = value.getAsJsonArray();
                    img.setUVSize(uvSize.get(0).getAsFloat(), uvSize.get(1).getAsFloat());
                }
                yield false;
            }

            // --- Button ---
            case "SetButtonDefault" -> {
                UIElement el = tree.findByName(bodyName);
                if (el instanceof UIElementButton btn && value != null) btn.setDefaultTexture(asString(value));
                yield false;
            }
            case "SetButtonHover" -> {
                UIElement el = tree.findByName(bodyName);
                if (el instanceof UIElementButton btn && value != null) btn.setHoverTexture(asString(value));
                yield false;
            }
            case "SetButtonPressed" -> {
                UIElement el = tree.findByName(bodyName);
                if (el instanceof UIElementButton btn && value != null) btn.setPressedTexture(asString(value));
                yield false;
            }
            case "SetButtonLabel" -> {
                UIElement el = tree.findByName(bodyName);
                if (el instanceof UIElementButton btn && value != null) btn.setButtonLabel(asString(value));
                yield false;
            }

            // --- Anchors ---
            case "SetAnchorFrom" -> {
                UIElement el = tree.findByName(bodyName);
                if (el != null && value != null) {
                    el.setAnchorFrom(AnchorPoint.fromString(asString(value)));
                    tree.markLayoutDirty();
                }
                yield false;
            }
            case "SetAnchorTo" -> {
                UIElement el = tree.findByName(bodyName);
                if (el != null && value != null) {
                    el.setAnchorTo(AnchorPoint.fromString(asString(value)));
                    tree.markLayoutDirty();
                }
                yield false;
            }

            // --- Clip ---
            case "SetClip" -> {
                UIElement el = tree.findByName(bodyName);
                if (el != null && value != null) el.setClip(value.getAsBoolean());
                yield false;
            }
            case "SetClipOffset" -> {
                UIElement el = tree.findByName(bodyName);
                if (el != null && value != null) {
                    JsonArray offset = value.getAsJsonArray();
                    el.setClipOffset(offset.get(0).getAsFloat(), offset.get(1).getAsFloat());
                }
                yield false;
            }

            // --- Rotation ---
            case "SetRotateAngle" -> {
                UIElement el = tree.findByName(bodyName);
                if (el instanceof UIElementImage img && value != null) img.setRotateAngle(value.getAsFloat());
                yield false;
            }
            case "SetRotatePivot" -> {
                UIElement el = tree.findByName(bodyName);
                if (el instanceof UIElementImage img && value != null) {
                    JsonArray pivot = value.getAsJsonArray();
                    img.setRotatePivot(pivot.get(0).getAsFloat(), pivot.get(1).getAsFloat());
                }
                yield false;
            }

            // --- Scroll ---
            case "SetScrollContentSize" -> {
                UIElement el = tree.findByName(bodyName);
                if (el instanceof UIElementScroll scroll && value != null) {
                    JsonArray size = value.getAsJsonArray();
                    scroll.setContentSizeExprX(SizeExpression.parse(asString(size.get(0))));
                    scroll.setContentSizeExprY(SizeExpression.parse(asString(size.get(1))));
                    tree.markLayoutDirty();
                }
                yield false;
            }
            case "SetScrollViewPos" -> {
                UIElement el = tree.findByName(bodyName);
                if (el instanceof UIElementScroll scroll && value != null) {
                    scroll.setScrollOffset(value.getAsFloat());
                    tree.markLayoutDirty();
                }
                yield false;
            }
            case "SetScrollViewPercentValue" -> {
                UIElement el = tree.findByName(bodyName);
                if (el instanceof UIElementScroll scroll && value != null) {
                    scroll.setScrollPercent(value.getAsInt());
                    tree.markLayoutDirty();
                }
                yield false;
            }

            // --- Draggable ---
            case "RefreshDraggableBoundary" -> {
                if (value != null && value.isJsonObject()) {
                    JsonObject obj = value.getAsJsonObject();
                    String draggableName = obj.has("draggableName")
                            ? obj.get("draggableName").getAsString() : null;
                    if (draggableName != null) {
                        UIElement el = tree.findByName(draggableName);
                        if (el instanceof UIElementDraggable drag) {
                            // Set position if provided
                            if (obj.has("draggablePosition")) {
                                JsonObject pos = obj.getAsJsonObject("draggablePosition");
                                float x = pos.has("x") ? pos.get("x").getAsFloat() : 0;
                                float y = pos.has("y") ? pos.get("y").getAsFloat() : 0;
                                drag.setDraggablePosition(x, y);
                                tree.markLayoutDirty();
                            }
                            // Set custom boundary if provided, otherwise reset
                            if (obj.has("draggableBoundary")) {
                                JsonObject boundary = obj.getAsJsonObject("draggableBoundary");
                                drag.setCustomBoundary(
                                        boundary.has("minX") ? boundary.get("minX").getAsFloat() : 0,
                                        boundary.has("maxX") ? boundary.get("maxX").getAsFloat() : 0,
                                        boundary.has("minY") ? boundary.get("minY").getAsFloat() : 0,
                                        boundary.has("maxY") ? boundary.get("maxY").getAsFloat() : 0
                                );
                            } else {
                                drag.resetBoundary();
                            }
                        }
                    }
                }
                yield false;
            }

            // --- Structure ---
            case "AddElement" -> {
                if (value != null && value.isJsonObject()) {
                    tree.addElementFromJson(value.getAsJsonObject());
                } else if (value != null) {
                    // value might be a JSON string containing element definition
                    String jsonStr = asString(value);
                    if (jsonStr != null) {
                        JsonObject elementJson = new com.google.gson.Gson().fromJson(jsonStr, JsonObject.class);
                        tree.addElementFromJson(elementJson);
                    }
                }
                yield true;
            }
            case "RemoveElement" -> {
                // bodyName is the element name to remove
                if (bodyName != null) {
                    tree.removeElementByName(bodyName);
                }
                yield true;
            }

            // --- Animation ---
            case "AddAnimations" -> {
                if (bodyName != null && value != null && value.isJsonArray()) {
                    UIElement el = tree.findByName(bodyName);
                    if (el != null) {
                        JsonArray animConfigs = value.getAsJsonArray();
                        List<UIAnimation> anims = new ArrayList<>();
                        for (JsonElement animElem : animConfigs) {
                            if (animElem.isJsonObject()) {
                                try {
                                    anims.add(UIAnimation.fromJson(animElem.getAsJsonObject()));
                                } catch (Exception e) {
                                    ModUIClient.LOGGER.warn("[UICommand] Failed to parse animation: {}", animElem, e);
                                }
                            }
                        }
                        if (!anims.isEmpty()) {
                            tree.getAnimationManager().addAnimations(bodyName, anims);
                        }
                    }
                }
                yield false;
            }

            // --- Stack control ---
            case "CloseStackUI" -> {
                UIManager.getInstance().handleStackClose();
                yield false;
            }

            default -> {
                ModUIClient.LOGGER.debug("[UICommand] Unhandled command: {}", commandName);
                yield false;
            }
        };
    }

    /**
     * Get a string field from a JsonObject.
     */
    private static String getStringField(JsonObject obj, String key) {
        JsonElement elem = obj.get(key);
        if (elem != null) {
            return asString(elem);
        }
        return null;
    }

    /**
     * Get a field from a JsonObject.
     */
    private static JsonElement getField(JsonObject obj, String key) {
        return obj.get(key);
    }

    /**
     * Convert a JsonElement to String.
     */
    private static String asString(JsonElement elem) {
        if (elem == null || elem.isJsonNull()) return null;
        if (!elem.isJsonPrimitive()) return elem.toString();
        return elem.getAsString();
    }

}
