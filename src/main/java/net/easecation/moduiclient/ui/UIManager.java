package net.easecation.moduiclient.ui;

import com.google.gson.JsonArray;
import net.easecation.moduiclient.ModUIClient;
import net.easecation.moduiclient.payload.ModUIPayload;
import net.easecation.moduiclient.protocol.PyRpcCodec;
import net.easecation.moduiclient.render.ModUIStackScreen;
import net.easecation.moduiclient.ui.command.UICommandProcessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Core UI lifecycle manager.
 * Manages HUD and Stack UI trees, handles initialization and command dispatch.
 *
 * Two modes:
 * - HUD: Always visible overlay (like health bars, score displays)
 * - Stack: Modal screen (like menus, dialogs) - opens as a Minecraft Screen
 */
public class UIManager {

    private static final UIManager INSTANCE = new UIManager();

    private boolean connected = false;
    private UITree hudTree;
    private UITree stackTree;
    private boolean hudInitialized = false;
    private boolean stackInitialized = false;

    // Pending commands buffered before UI initialization
    private final List<PendingCommand> pendingHudCommands = new ArrayList<>();
    private final List<PendingCommand> pendingStackCommands = new ArrayList<>();

    // Track pending world change that occurred before CONFIRM handshake
    private boolean pendingWorldChange = false;

    // Screen info tracking + debounce
    private int lastScreenW = -1, lastScreenH = -1;
    private int lastViewW = -1, lastViewH = -1;
    private long screenResizeTimestamp = 0;
    private static final long SCREEN_RESIZE_DEBOUNCE_MS = 300;

    private UIManager() {}

    public static UIManager getInstance() {
        return INSTANCE;
    }

    // --- Connection ---

    public void setConnected(boolean connected) {
        ModUIClient.LOGGER.info("[UIManager] setConnected({}) called, pendingWorldChange={}", connected, pendingWorldChange);
        this.connected = connected;
        if (!connected) {
            reset();
        } else if (pendingWorldChange) {
            // WORLD_CHANGE fired before CONFIRM — process it now
            pendingWorldChange = false;
            ModUIClient.LOGGER.info("[UIManager] Processing pending world change after connection confirmed");
            clearHud();
            sendCurrentScreenInfo();
            requestHud();
        }
    }

    public boolean isConnected() {
        return connected;
    }

    // --- HUD ---

    public void handleHudInit(String uiJson) {
        ModUIClient.LOGGER.info("[UIManager] handleHudInit() called, hudInitialized={}, hudTree={}, uiJson={} chars",
                hudInitialized, hudTree != null ? "exists" : "null", uiJson.length());
        if (hudInitialized && hudTree != null) {
            ModUIClient.LOGGER.info("[UIManager] HUD already initialized, ignoring duplicate init");
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        int w = client.getWindow().getScaledWidth();
        int h = client.getWindow().getScaledHeight();

        hudTree = new UITree(w, h);
        hudTree.initFromJson(uiJson);
        hudInitialized = true;

        // Replay pending commands
        if (!pendingHudCommands.isEmpty()) {
            ModUIClient.LOGGER.debug("[UIManager] Replaying {} pending HUD commands", pendingHudCommands.size());
            for (PendingCommand cmd : pendingHudCommands) {
                UICommandProcessor.processBatch(hudTree, cmd.commands);
            }
            pendingHudCommands.clear();
        }
    }

    // --- Stack ---

    public void handleStackCreate(String uiJson) {
        ModUIClient.LOGGER.info("[UIManager] Stack create received ({} chars)", uiJson.length());
        MinecraftClient client = MinecraftClient.getInstance();
        int w = client.getWindow().getScaledWidth();
        int h = client.getWindow().getScaledHeight();

        stackTree = new UITree(w, h);
        stackTree.initFromJson(uiJson);
        stackInitialized = true;

        // Open as Screen
        client.execute(() -> client.setScreen(new ModUIStackScreen(stackTree)));

        // Replay pending commands
        if (!pendingStackCommands.isEmpty()) {
            ModUIClient.LOGGER.debug("[UIManager] Replaying {} pending Stack commands", pendingStackCommands.size());
            for (PendingCommand cmd : pendingStackCommands) {
                UICommandProcessor.processBatch(stackTree, cmd.commands);
            }
            pendingStackCommands.clear();
        }
    }

    /**
     * Handle stack close from server (RequestRemoveStackNodeEvent).
     */
    public void handleStackClose() {
        ModUIClient.LOGGER.info("[UIManager] Stack close received");
        stackTree = null;
        stackInitialized = false;
        pendingStackCommands.clear();

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen instanceof ModUIStackScreen) {
            client.execute(() -> client.setScreen(null));
        }
    }

    /**
     * Handle stack close initiated by user (ESC key).
     * Sends global_close event to server.
     */
    public void handleStackCloseByUser() {
        ModUIClient.LOGGER.info("[UIManager] Stack close by user (ESC)");
        sendStackButtonClick("global_close", "global_close");

        stackTree = null;
        stackInitialized = false;
        pendingStackCommands.clear();
    }

    // --- C2S Button Click ---

    /**
     * Send a Stack button click event to the server.
     * @param buttonPath full path of the button (e.g. "/bg_panel/btn_close")
     * @param buttonName last segment of path (e.g. "btn_close")
     */
    public void sendStackButtonClick(String buttonPath, String buttonName) {
        if (!connected) return;
        byte[] data = PyRpcCodec.buildButtonClickC2S(
                "RequestClickStackBtEvent", 0, buttonPath, buttonName);
        ModUIPayload.sendC2S(data);
        ModUIClient.LOGGER.debug("[UIManager] Sent Stack button click: path={}, name={}", buttonPath, buttonName);
    }

    /**
     * Send a HUD button click event to the server.
     * @param buttonPath full path of the button
     * @param buttonName last segment of path
     */
    public void sendHudButtonClick(String buttonPath, String buttonName) {
        if (!connected) return;
        byte[] data = PyRpcCodec.buildButtonClickC2S(
                "RequestClickHudBtEvent", 0, buttonPath, buttonName);
        ModUIPayload.sendC2S(data);
        ModUIClient.LOGGER.debug("[UIManager] Sent HUD button click: path={}, name={}", buttonPath, buttonName);
    }

    // --- Key Press ---

    /**
     * Send a keyboard key press/release event to the server.
     * Maps to Bedrock's OnKeyPressInGame → NotifyToServer("KeyPressInGame", {key, isDown}).
     *
     * @param key   Windows VK code as string (e.g. "87" for W)
     * @param isDown "1" for press, "0" for release
     */
    public void sendKeyPress(String key, String isDown) {
        if (!connected) return;
        byte[] data = PyRpcCodec.buildKeyPressC2S(key, isDown);
        if (data != null) {
            ModUIPayload.sendC2S(data);
        }
    }

    // --- Screen Info ---

    /**
     * Send screen resolution info to the server.
     */
    public void sendScreenInfo(int screenW, int screenH, int viewW, int viewH) {
        if (!connected) return;
        byte[] data = PyRpcCodec.buildScreenInfoC2S(screenW, screenH, viewW, viewH);
        if (data != null) {
            ModUIPayload.sendC2S(data);
            ModUIClient.LOGGER.info("[UIManager] Sent ScreenInfo: screen={}x{}, view={}x{}",
                    screenW, screenH, viewW, viewH);
        }
    }

    /**
     * Send current screen info immediately (called on world change / dimension change).
     */
    public void sendCurrentScreenInfo() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getWindow() == null) return;
        int screenW = client.getWindow().getWidth();
        int screenH = client.getWindow().getHeight();
        int viewW = client.getWindow().getScaledWidth();
        int viewH = client.getWindow().getScaledHeight();
        lastScreenW = screenW;
        lastScreenH = screenH;
        lastViewW = viewW;
        lastViewH = viewH;
        sendScreenInfo(screenW, screenH, viewW, viewH);
    }

    // --- HUD Request ---

    /**
     * Send RequestHudNodeDataEvent to server.
     */
    public void requestHud() {
        ModUIClient.LOGGER.info("[UIManager] requestHud() called — sending RequestHudNodeDataEvent C2S");
        byte[] data = PyRpcCodec.buildHudRequestC2S();
        if (data != null) {
            ModUIPayload.sendC2S(data);
        }
    }

    public void setPendingWorldChange(boolean pending) { this.pendingWorldChange = pending; }

    // --- Commands ---

    public void handleCommand(String uiName, JsonArray commandList) {
        ModUIClient.LOGGER.info("[UIManager] handleCommand() uiName={}, commands={}, hudInitialized={}, hudTree={}",
                uiName, commandList.size(), hudInitialized, hudTree != null ? "exists" : "null");
        if ("nukkitHudUI".equals(uiName)) {
            if (!hudInitialized || hudTree == null) {
                ModUIClient.LOGGER.info("[UIManager] HUD not ready, buffering {} commands (total pending={})",
                        commandList.size(), pendingHudCommands.size() + 1);
                pendingHudCommands.add(new PendingCommand(commandList));
                return;
            }
            // Log command names for debugging
            for (int i = 0; i < commandList.size(); i++) {
                var cmd = commandList.get(i);
                if (cmd.isJsonObject()) {
                    var obj = cmd.getAsJsonObject();
                    ModUIClient.LOGGER.info("[UIManager]   cmd[{}]: {} bodyName={}", i,
                            obj.has("command") ? obj.get("command").getAsString() : "?",
                            obj.has("bodyName") ? obj.get("bodyName").getAsString() : "?");
                }
            }
            UICommandProcessor.processBatch(hudTree, commandList);
        } else if ("nukkitStackUI".equals(uiName)) {
            if (!stackInitialized || stackTree == null) {
                pendingStackCommands.add(new PendingCommand(commandList));
                return;
            }
            UICommandProcessor.processBatch(stackTree, commandList);
        } else {
            ModUIClient.LOGGER.debug("[UIManager] Unknown UI name: {}", uiName);
        }
    }

    // --- Rendering ---

    public void renderHud(DrawContext context, float tickDelta) {
        if (!connected) return;

        MinecraftClient client = MinecraftClient.getInstance();
        int screenW = client.getWindow().getWidth();
        int screenH = client.getWindow().getHeight();
        int viewW = client.getWindow().getScaledWidth();
        int viewH = client.getWindow().getScaledHeight();

        // Detect screen size change and debounce ScreenInfoEvent
        if (screenW != lastScreenW || screenH != lastScreenH
                || viewW != lastViewW || viewH != lastViewH) {
            lastScreenW = screenW;
            lastScreenH = screenH;
            lastViewW = viewW;
            lastViewH = viewH;
            screenResizeTimestamp = System.currentTimeMillis();
        }
        if (screenResizeTimestamp > 0
                && System.currentTimeMillis() - screenResizeTimestamp >= SCREEN_RESIZE_DEBOUNCE_MS) {
            screenResizeTimestamp = 0;
            sendScreenInfo(screenW, screenH, viewW, viewH);
        }

        if (!hudInitialized || hudTree == null) return;

        hudTree.updateLayout(viewW, viewH);
        hudTree.getRoot().renderTree(context, tickDelta);
    }

    // --- Reset ---

    /**
     * Clear HUD only (called on world change, does not affect Stack).
     * Mimics Chinese client behavior: clear and re-request HUD on every world switch.
     */
    public void clearHud() {
        ModUIClient.LOGGER.info("[UIManager] clearHud() called, was hudInitialized={}", hudInitialized);
        hudTree = null;
        hudInitialized = false;
        pendingHudCommands.clear();
    }

    public void reset() {
        hudTree = null;
        stackTree = null;
        hudInitialized = false;
        stackInitialized = false;
        pendingHudCommands.clear();
        pendingStackCommands.clear();
        pendingWorldChange = false;
        NineSliceInfo.clearCache();
    }

    // --- Getters ---

    public UITree getHudTree() { return hudTree; }
    public UITree getStackTree() { return stackTree; }
    public boolean isHudInitialized() { return hudInitialized; }

    private record PendingCommand(JsonArray commands) {}
}
