package net.easecation.moduiclient.mixin;

import net.easecation.moduiclient.ModUIClient;
import net.easecation.moduiclient.input.GlfwToVkMapper;
import net.easecation.moduiclient.ui.UIManager;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts keyboard events and forwards them to the server as KeyPressInGame events.
 * Matches Bedrock's OnKeyPressInGame behavior:
 *   args = {key: "vk_code_string", isDown: "1" or "0"}
 *
 * MC 1.21.11 signature: onKey(long window, int action, KeyInput keyInput)
 * KeyInput is a record containing (key, scancode, modifiers).
 */
@Mixin(Keyboard.class)
public class KeyboardMixin {

    @Inject(method = "onKey", at = @At("HEAD"))
    private void onKeyPress(long window, int action, KeyInput keyInput, CallbackInfo ci) {
        // Only handle PRESS and RELEASE, skip REPEAT
        ModUIClient.LOGGER.info("[KeyboardMixin] onKey: action={}, key={}, scancode={}", action, keyInput.key(), keyInput.scancode());
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_RELEASE) return;

        // Only send when connected
        UIManager manager = UIManager.getInstance();
        if (!manager.isConnected()) return;

        // Only send "in game" events: no screen open, or ModUI Stack screen open
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.currentScreen != null
                && !(client.currentScreen instanceof net.easecation.moduiclient.render.ModUIStackScreen)) {
            return;
        }

        // Map GLFW key code to Windows VK code
        int vk = GlfwToVkMapper.toVk(keyInput.key());
        if (vk < 0) return;

        String screenName = (client.currentScreen instanceof net.easecation.moduiclient.render.ModUIStackScreen)
                ? "nukkitStackUI" : "hud_screen";

        String isDown = (action == GLFW.GLFW_PRESS) ? "1" : "0";
        manager.sendKeyPress(screenName, String.valueOf(vk), isDown);
    }
}
