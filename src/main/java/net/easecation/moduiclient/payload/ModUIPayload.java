package net.easecation.moduiclient.payload;

import net.easecation.moduiclient.ModUIClient;
import net.easecation.moduiclient.protocol.PyRpcCodec;
import net.easecation.moduiclient.ui.UIManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public class ModUIPayload implements CustomPayload {

    public static final Id<ModUIPayload> ID = new Id<>(Identifier.of(ModUIClient.MOD_ID, "data"));

    public static final PacketCodec<PacketByteBuf, ModUIPayload> STREAM_CODEC = PacketCodec.of(
            (payload, buf) -> {
                // Encode (C2S)
                buf.writeInt(payload.type.ordinal());
                if (payload.data != null) {
                    buf.writeBytes(payload.data);
                }
            },
            buf -> {
                int type = buf.readInt();
                switch (type) {
                    case 0: // CONFIRM
                        // Note: setConnected(true) is called in handle() on the render thread, not here (network thread)
                        return new ModUIPayload(PayloadType.CONFIRM, null);
                    case 1: // PY_RPC_DATA
                        byte[] msgpackData = new byte[buf.readableBytes()];
                        buf.readBytes(msgpackData);
                        return new ModUIPayload(PayloadType.PY_RPC_DATA, msgpackData);
                    default:
                        throw new IllegalStateException("Unknown ModUI payload type: " + type);
                }
            }
    );

    private final PayloadType type;
    private final byte[] data;

    public ModUIPayload(PayloadType type, byte[] data) {
        this.type = type;
        this.data = data;
    }

    public void handle() {
        switch (type) {
            case CONFIRM:
                ModUIClient.LOGGER.info("[Handshake] ModUI channel confirmed");
                UIManager.getInstance().setConnected(true);
                // Don't send HudRequest here; WORLD_CHANGE handler handles it
                // via pendingWorldChange mechanism to avoid double requests
                break;
            case PY_RPC_DATA:
                if (data != null) {
                    PyRpcCodec.handleS2C(data);
                }
                break;
        }
    }

    /**
     * Send C2S PY_RPC data to the server via CustomPayload.
     */
    public static void sendC2S(byte[] msgpackData) {
        if (msgpackData == null) return;
        try {
            ClientPlayNetworking.send(new ModUIPayload(PayloadType.PY_RPC_DATA, msgpackData));
        } catch (Exception e) {
            ModUIClient.LOGGER.error("[ModUIPayload] Failed to send C2S payload", e);
        }
    }

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }

    public enum PayloadType {
        CONFIRM, PY_RPC_DATA
    }

}
