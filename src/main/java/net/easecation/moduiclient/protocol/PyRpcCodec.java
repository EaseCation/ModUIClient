package net.easecation.moduiclient.protocol;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.easecation.moduiclient.ModUIClient;
import net.easecation.moduiclient.ui.UIManager;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.msgpack.value.Value;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Parses PY_RPC MsgPack data and dispatches ModEventS2C events to UIManager.
 * Also builds C2S MsgPack data for ModEventC2S events.
 *
 * PY_RPC S2C format (MsgPack):
 *   ["ModEventS2C", [modName, systemName, eventName, eventData], nil]
 *
 * PY_RPC C2S format (MsgPack):
 *   ["ModEventC2S", [modName, systemName, eventName, eventData], nil]
 */
public class PyRpcCodec {

    private static final Gson GSON = new Gson();

    private static final String MOD_NAME = "ECNukkitClientMod";
    private static final String CLIENT_SYSTEM = "ECNukkitClientSystem";

    // --- S2C ---

    public static void handleS2C(byte[] msgpackData) {
        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(msgpackData)) {
            if (!unpacker.hasNext()) return;
            Value value = unpacker.unpackValue();

            String json = value.toJson();
            JsonArray array = GSON.fromJson(json, JsonArray.class);
            if (array == null || array.isEmpty()) return;

            String type = getAsString(array.get(0));
            if (!"ModEventS2C".equals(type)) {
                ModUIClient.LOGGER.debug("[PyRpc] Ignoring non-ModEventS2C type: {}", type);
                return;
            }

            if (array.size() < 2 || !array.get(1).isJsonArray()) return;
            JsonArray params = array.get(1).getAsJsonArray();
            if (params.size() < 4) return;

            String modName = getAsString(params.get(0));
            String systemName = getAsString(params.get(1));
            String eventName = getAsString(params.get(2));
            JsonElement eventData = params.get(3);

            ModUIClient.LOGGER.info("[PyRpc] S2C event: event={}", eventName);
            dispatchEvent(eventName, eventData);
        } catch (IOException e) {
            ModUIClient.LOGGER.error("[PyRpc] Failed to parse MsgPack data", e);
        } catch (Exception e) {
            ModUIClient.LOGGER.error("[PyRpc] Failed to dispatch event", e);
        }
    }

    private static void dispatchEvent(String eventName, JsonElement eventData) {
        UIManager manager = UIManager.getInstance();
        switch (eventName) {
            case "ResponseHudNodeDataEvent":
                handleResponseHudNodeData(manager, eventData);
                break;
            case "RequestCreateStackNodeEvent":
                handleRequestCreateStackNode(manager, eventData);
                break;
            case "RequestControlNodeEvent":
                handleRequestControlNode(manager, eventData);
                break;
            case "RequestRemoveStackNodeEvent":
                manager.handleStackClose();
                break;
            default:
                ModUIClient.LOGGER.debug("[PyRpc] Unhandled event: {}", eventName);
                break;
        }
    }

    private static void handleResponseHudNodeData(UIManager manager, JsonElement eventData) {
        if (!eventData.isJsonObject()) return;
        JsonObject obj = eventData.getAsJsonObject();
        String uiJson = getJsonString(obj, "uiJson");
        if (uiJson != null) {
            manager.handleHudInit(uiJson);
        }
    }

    private static void handleRequestCreateStackNode(UIManager manager, JsonElement eventData) {
        if (!eventData.isJsonObject()) return;
        JsonObject obj = eventData.getAsJsonObject();
        String uiJson = getJsonString(obj, "uiJson");
        if (uiJson != null) {
            manager.handleStackCreate(uiJson);
        }
    }

    private static void handleRequestControlNode(UIManager manager, JsonElement eventData) {
        if (!eventData.isJsonObject()) return;
        JsonObject obj = eventData.getAsJsonObject();
        String uiName = getJsonString(obj, "uiName");
        JsonElement commandListElem = obj.get("commandList");
        if (uiName != null && commandListElem != null && commandListElem.isJsonArray()) {
            manager.handleCommand(uiName, commandListElem.getAsJsonArray());
        }
    }

    // --- C2S ---

    /**
     * Build C2S MsgPack bytes for RequestHudNodeDataEvent.
     * Triggers the server to send HUD JSON data.
     */
    public static byte[] buildHudRequestC2S() {
        try (MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
            packer.packArrayHeader(3);
            packStr(packer, "ModEventC2S");

            packer.packArrayHeader(4);
            packStr(packer, MOD_NAME);
            packStr(packer, CLIENT_SYSTEM);
            packStr(packer, "RequestHudNodeDataEvent");
            packer.packMapHeader(0);

            packer.packNil();
            return packer.toByteArray();
        } catch (IOException e) {
            ModUIClient.LOGGER.error("[PyRpc] Failed to build C2S HudRequest", e);
            return null;
        }
    }

    /**
     * Build C2S MsgPack bytes for a button click event.
     * Format: ["ModEventC2S", [modName, systemName, eventName, eventData], nil]
     *
     * @param eventName "RequestClickHudBtEvent" or "RequestClickStackBtEvent"
     * @param touchEvent touch event type (0=TOUCH_UP)
     * @param buttonPath full button path (e.g. "/bg_panel/btn_close")
     * @param buttonName last segment of path (e.g. "btn_close")
     */
    public static byte[] buildButtonClickC2S(String eventName, int touchEvent, String buttonPath, String buttonName) {
        try (MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
            // Outer array: ["ModEventC2S", [...], nil]
            packer.packArrayHeader(3);
            packStr(packer, "ModEventC2S");

            // Inner array: [modName, systemName, eventName, eventData]
            packer.packArrayHeader(4);
            packStr(packer, MOD_NAME);
            packStr(packer, CLIENT_SYSTEM);
            packStr(packer, eventName);

            // eventData map: {TouchEvent: int, ButtonPath: str, buttonName: str, playerId: str}
            packer.packMapHeader(4);
            packStr(packer, "TouchEvent");
            packer.packInt(touchEvent);
            packStr(packer, "ButtonPath");
            packStr(packer, buttonPath);
            packStr(packer, "buttonName");
            packStr(packer, buttonName);
            packStr(packer, "playerId");
            packStr(packer, "");

            // nil (third element)
            packer.packNil();

            return packer.toByteArray();
        } catch (IOException e) {
            ModUIClient.LOGGER.error("[PyRpc] Failed to build C2S button click", e);
            return null;
        }
    }

    /**
     * Build C2S MsgPack bytes for ScreenInfoEvent.
     * Sends screen resolution info to server.
     *
     * @param screenW physical screen width (pixels)
     * @param screenH physical screen height (pixels)
     * @param viewW   GUI-scaled viewport width
     * @param viewH   GUI-scaled viewport height
     */
    public static byte[] buildScreenInfoC2S(int screenW, int screenH, int viewW, int viewH) {
        try (MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
            packer.packArrayHeader(3);
            packStr(packer, "ModEventC2S");

            packer.packArrayHeader(4);
            packStr(packer, MOD_NAME);
            packStr(packer, CLIENT_SYSTEM);
            packStr(packer, "ScreenInfoEvent");

            // eventData: { screen: {width, height}, view: {width, height, offsetX, offsetY} }
            packer.packMapHeader(2);

            packStr(packer, "screen");
            packer.packMapHeader(2);
            packStr(packer, "width");
            packer.packInt(screenW);
            packStr(packer, "height");
            packer.packInt(screenH);

            packStr(packer, "view");
            packer.packMapHeader(4);
            packStr(packer, "width");
            packer.packInt(viewW);
            packStr(packer, "height");
            packer.packInt(viewH);
            packStr(packer, "offsetX");
            packer.packInt(0);
            packStr(packer, "offsetY");
            packer.packInt(0);

            packer.packNil();
            return packer.toByteArray();
        } catch (IOException e) {
            ModUIClient.LOGGER.error("[PyRpc] Failed to build C2S ScreenInfo", e);
            return null;
        }
    }

    /**
     * Build C2S MsgPack bytes for KeyPressInGame event.
     * Sends keyboard key press/release to server (mirrors Bedrock OnKeyPressInGame).
     *
     * @param key   Windows VK code as string (e.g. "87" for W)
     * @param isDown "1" for press, "0" for release
     */
    public static byte[] buildKeyPressC2S(String key, String isDown) {
        try (MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
            packer.packArrayHeader(3);
            packStr(packer, "ModEventC2S");

            packer.packArrayHeader(4);
            packStr(packer, MOD_NAME);
            packStr(packer, CLIENT_SYSTEM);
            packStr(packer, "KeyPressInGame");

            // eventData: {key: "87", isDown: "1"}  — both values are strings
            packer.packMapHeader(2);
            packStr(packer, "key");
            packStr(packer, key);
            packStr(packer, "isDown");
            packStr(packer, isDown);

            packer.packNil();
            return packer.toByteArray();
        } catch (IOException e) {
            ModUIClient.LOGGER.error("[PyRpc] Failed to build C2S KeyPressInGame", e);
            return null;
        }
    }

    /**
     * Pack a string as MsgPack StringValue.
     * Server uses Value.toJson() → GSON; BinaryValue.toJson() base64-encodes which breaks
     * string matching, so we must use StringValue (packString) instead.
     */
    private static void packStr(MessageBufferPacker packer, String value) throws IOException {
        packer.packString(value);
    }

    // --- Utility ---

    private static String getAsString(JsonElement elem) {
        if (elem == null || elem.isJsonNull()) return null;
        if (elem.isJsonPrimitive()) {
            return elem.getAsString();
        }
        return elem.toString();
    }

    private static String getJsonString(JsonObject obj, String key) {
        if (!obj.has(key)) return null;
        JsonElement elem = obj.get(key);
        if (elem.isJsonNull()) return null;
        if (elem.isJsonPrimitive()) {
            return elem.getAsString();
        }
        return elem.toString();
    }

}
