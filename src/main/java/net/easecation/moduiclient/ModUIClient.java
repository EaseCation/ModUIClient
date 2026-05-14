package net.easecation.moduiclient;

import net.easecation.moduiclient.entity.EntityMappingStore;
import net.easecation.moduiclient.protocol.PyRpcCodec;
import net.easecation.moduiclient.render.HudLayerRenderer;
import net.easecation.moduiclient.ui.NineSliceInfo;
import net.easecation.moduiclient.ui.UIManager;
import net.easecation.moduiclient.ui.element.UIElementImage;
import net.easecation.neteasebridge.client.fabric.NeteaseRpcEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.fabric.impl.networking.RegistrationPayload;
import net.minecraft.network.packet.c2s.common.CustomPayloadC2SPacket;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class ModUIClient implements ClientModInitializer {

    public static final String MOD_ID = "moduiclient";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private static final String MOD_EVENT_NAMESPACE = "ECNukkitClientMod";
    private static final String MOD_EVENT_SERVER_SYSTEM = "ECNukkitServerSystem";

    @Override
    public void onInitializeClient() {
        LOGGER.info("[ModUIClient] Initializing...");

        NeteaseRpcEvents.CONNECTED.register(() -> UIManager.getInstance().setConnected(true));
        NeteaseRpcEvents.DISCONNECTED.register(() -> {
            UIManager.getInstance().setConnected(false);
            EntityMappingStore.getInstance().clear();
        });
        NeteaseRpcEvents.MOD_EVENT_S2C.register(event -> {
            if (MOD_EVENT_NAMESPACE.equals(event.namespace()) && MOD_EVENT_SERVER_SYSTEM.equals(event.system())) {
                PyRpcCodec.handleS2C(event.rawPayload());
            } else {
                LOGGER.debug("[ModUIClient] Ignoring ModEventS2C namespace={}, system={}, event={}",
                        event.namespace(), event.system(), event.eventName());
            }
        });
        NeteaseRpcEvents.ENTITY_MAPPING_S2C.register(data -> EntityMappingStore.getInstance().handlePayload(data));

        // Register HUD renderer
        HudLayerRenderer.register();
        registerResourceReloadListener();

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

    private void registerResourceReloadListener() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return Identifier.of(MOD_ID, "modui_cache_clear");
            }

            @Override
            public void reload(ResourceManager manager) {
                NineSliceInfo.clearCache();
                UIElementImage.clearTextureDimensionCache();
                LOGGER.debug("[ModUIClient] Cleared ModUI texture caches after client resource reload");
            }
        });
    }

}
