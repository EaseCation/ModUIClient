package net.easecation.moduiclient;

import net.easecation.moduiclient.entity.EntityMappingStore;
import net.easecation.moduiclient.protocol.PyRpcCodec;
import net.easecation.moduiclient.render.HudLayerRenderer;
import net.easecation.moduiclient.ui.UIManager;
import net.easecation.neteasebridge.client.fabric.NeteaseRpcEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.impl.networking.RegistrationPayload;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ModUIClient implements ClientModInitializer {

    public static final String MOD_ID = "moduiclient";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final String MOD_EVENT_NAMESPACE = "ECNukkitClientMod";
    private static final String MOD_EVENT_SYSTEM = "ECNukkitClientSystem";

    @Override
    public void onInitializeClient() {
        LOGGER.info("[ModUIClient] Initializing...");

        NeteaseRpcEvents.CONNECTED.register(() -> UIManager.getInstance().setConnected(true));
        NeteaseRpcEvents.DISCONNECTED.register(() -> {
            UIManager.getInstance().setConnected(false);
            EntityMappingStore.getInstance().clear();
        });
        NeteaseRpcEvents.MOD_EVENT_S2C.register(event -> {
            if (MOD_EVENT_NAMESPACE.equals(event.namespace()) && MOD_EVENT_SYSTEM.equals(event.system())) {
                PyRpcCodec.handleS2C(event.rawPayload());
            }
        });
        NeteaseRpcEvents.ENTITY_MAPPING_S2C.register(data -> EntityMappingStore.getInstance().handlePayload(data));

        // Register HUD renderer
        HudLayerRenderer.register();

        // Connection lifecycle
        // On JOIN: register moduiclient:confirm channel to trigger ViaBedrock handshake.
        // Must be done during PLAY state so ViaBedrock can enable ModUI-only entity mapping.
        // setConnected(true) means the shared transport is available; RequestHud confirms ModUI to the server.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            LOGGER.info("[ModUIClient] JOIN event fired, connected={}, registering moduiclient:confirm channel...",
                    UIManager.getInstance().isConnected());
            handler.sendPacket(new CustomPayloadC2SPacket(new RegistrationPayload(
                    RegistrationPayload.REGISTER,
                    List.of(Identifier.of(MOD_ID, "confirm"))
            )));
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            LOGGER.info("[ModUIClient] Disconnected");
        });

        // World change (initial join + dimension change + cross-server transfer)
        // Mimics Chinese client flow: clear HUD and re-request on every world change
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, world) -> {
            UIManager manager = UIManager.getInstance();
            LOGGER.info("[ModUIClient] WORLD_CHANGE event fired, connected={}, hudInitialized={}",
                    manager.isConnected(), manager.isHudInitialized());
            if (!manager.isConnected()) {
                LOGGER.info("[ModUIClient] Not connected yet, setting pendingWorldChange=true");
                manager.setPendingWorldChange(true);
                return;
            }
            LOGGER.info("[ModUIClient] World changed, clearing HUD and requesting new HUD...");
            manager.clearHud();
            manager.sendCurrentScreenInfo();
            manager.requestHud();
        });

        LOGGER.info("[ModUIClient] Initialized.");
    }

}
