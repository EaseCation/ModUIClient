package net.easecation.moduiclient.input;

import org.lwjgl.glfw.GLFW;

/**
 * Maps GLFW key codes to Windows Virtual Key (VK) codes.
 * The Bedrock/Nukkit server expects Windows VK codes as strings.
 *
 * Letters (A-Z) and digits (0-9) share the same codes between GLFW and VK.
 * Special keys need explicit mapping.
 */
public class GlfwToVkMapper {

    /**
     * Convert a GLFW key code to a Windows VK code.
     * @return the VK code, or -1 if unmapped
     */
    public static int toVk(int glfwKey) {
        // Letters A-Z: GLFW 65-90 == VK 65-90
        if (glfwKey >= GLFW.GLFW_KEY_A && glfwKey <= GLFW.GLFW_KEY_Z) return glfwKey;
        // Digits 0-9: GLFW 48-57 == VK 48-57
        if (glfwKey >= GLFW.GLFW_KEY_0 && glfwKey <= GLFW.GLFW_KEY_9) return glfwKey;
        // Space
        if (glfwKey == GLFW.GLFW_KEY_SPACE) return 0x20;

        return switch (glfwKey) {
            // Navigation
            case GLFW.GLFW_KEY_ESCAPE -> 0x1B;
            case GLFW.GLFW_KEY_ENTER -> 0x0D;
            case GLFW.GLFW_KEY_TAB -> 0x09;
            case GLFW.GLFW_KEY_BACKSPACE -> 0x08;
            case GLFW.GLFW_KEY_INSERT -> 0x2D;
            case GLFW.GLFW_KEY_DELETE -> 0x2E;
            case GLFW.GLFW_KEY_RIGHT -> 0x27;
            case GLFW.GLFW_KEY_LEFT -> 0x25;
            case GLFW.GLFW_KEY_DOWN -> 0x28;
            case GLFW.GLFW_KEY_UP -> 0x26;
            case GLFW.GLFW_KEY_PAGE_UP -> 0x21;
            case GLFW.GLFW_KEY_PAGE_DOWN -> 0x22;
            case GLFW.GLFW_KEY_HOME -> 0x24;
            case GLFW.GLFW_KEY_END -> 0x23;
            // Lock keys
            case GLFW.GLFW_KEY_CAPS_LOCK -> 0x14;
            case GLFW.GLFW_KEY_SCROLL_LOCK -> 0x91;
            case GLFW.GLFW_KEY_NUM_LOCK -> 0x90;
            // Function keys F1-F12
            case GLFW.GLFW_KEY_F1 -> 0x70;
            case GLFW.GLFW_KEY_F2 -> 0x71;
            case GLFW.GLFW_KEY_F3 -> 0x72;
            case GLFW.GLFW_KEY_F4 -> 0x73;
            case GLFW.GLFW_KEY_F5 -> 0x74;
            case GLFW.GLFW_KEY_F6 -> 0x75;
            case GLFW.GLFW_KEY_F7 -> 0x76;
            case GLFW.GLFW_KEY_F8 -> 0x77;
            case GLFW.GLFW_KEY_F9 -> 0x78;
            case GLFW.GLFW_KEY_F10 -> 0x79;
            case GLFW.GLFW_KEY_F11 -> 0x7A;
            case GLFW.GLFW_KEY_F12 -> 0x7B;
            // Modifier keys (L/R both map to same VK)
            case GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT -> 0x10;
            case GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL -> 0x11;
            case GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT -> 0x12;
            // Numpad
            case GLFW.GLFW_KEY_KP_0 -> 0x60;
            case GLFW.GLFW_KEY_KP_1 -> 0x61;
            case GLFW.GLFW_KEY_KP_2 -> 0x62;
            case GLFW.GLFW_KEY_KP_3 -> 0x63;
            case GLFW.GLFW_KEY_KP_4 -> 0x64;
            case GLFW.GLFW_KEY_KP_5 -> 0x65;
            case GLFW.GLFW_KEY_KP_6 -> 0x66;
            case GLFW.GLFW_KEY_KP_7 -> 0x67;
            case GLFW.GLFW_KEY_KP_8 -> 0x68;
            case GLFW.GLFW_KEY_KP_9 -> 0x69;
            case GLFW.GLFW_KEY_KP_DECIMAL -> 0x6E;
            case GLFW.GLFW_KEY_KP_DIVIDE -> 0x6F;
            case GLFW.GLFW_KEY_KP_MULTIPLY -> 0x6A;
            case GLFW.GLFW_KEY_KP_SUBTRACT -> 0x6D;
            case GLFW.GLFW_KEY_KP_ADD -> 0x6B;
            case GLFW.GLFW_KEY_KP_ENTER -> 0x0D;
            // Punctuation
            case GLFW.GLFW_KEY_MINUS -> 0xBD;
            case GLFW.GLFW_KEY_EQUAL -> 0xBB;
            case GLFW.GLFW_KEY_LEFT_BRACKET -> 0xDB;
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> 0xDD;
            case GLFW.GLFW_KEY_BACKSLASH -> 0xDC;
            case GLFW.GLFW_KEY_SEMICOLON -> 0xBA;
            case GLFW.GLFW_KEY_APOSTROPHE -> 0xDE;
            case GLFW.GLFW_KEY_GRAVE_ACCENT -> 0xC0;
            case GLFW.GLFW_KEY_COMMA -> 0xBC;
            case GLFW.GLFW_KEY_PERIOD -> 0xBE;
            case GLFW.GLFW_KEY_SLASH -> 0xBF;
            default -> -1;
        };
    }
}
