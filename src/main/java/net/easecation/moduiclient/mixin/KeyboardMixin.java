package net.easecation.moduiclient.mixin;

import net.easecation.moduiclient.input.GlfwToVkMapper;
import net.easecation.moduiclient.ui.UIManager;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts keyboard events and forwards them to the server as KeyPressInGame events.
 * Matches Bedrock's OnKeyPressInGame behavior:
 *   args = {key: "vk_code_string", isDown: "1" or "0"}
 */
@Mixin(Keyboard.class)
public class KeyboardMixin {

    @Inject(method = "onKey", at = @At("HEAD"))
    private void onKeyPress(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        // Only handle PRESS and RELEASE, skip REPEAT
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
        int vk = GlfwToVkMapper.toVk(key);
        if (vk < 0) return;

        String isDown = (action == GLFW.GLFW_PRESS) ? "1" : "0";
        manager.sendKeyPress(String.valueOf(vk), isDown);
    }
}
