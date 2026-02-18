package net.easecation.moduiclient.render;

import net.easecation.moduiclient.ModUIClient;
import net.easecation.moduiclient.ui.UIManager;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.util.Identifier;

/**
 * Registers the ModUI HUD layer for rendering HUD elements.
 */
public class HudLayerRenderer {

    private static final Identifier HUD_LAYER_ID = Identifier.of(ModUIClient.MOD_ID, "hud");

    public static void register() {
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CHAT,
                HUD_LAYER_ID,
                (context, tickCounter) -> UIManager.getInstance().renderHud(context, tickCounter.getTickProgress(false))
        );
    }

}
