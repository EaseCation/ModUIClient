package net.easecation.moduiclient.entity;

import io.netty.buffer.Unpooled;
import net.easecation.moduiclient.ModUIClient;
import net.minecraft.network.PacketByteBuf;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Maintains a mapping of Bedrock runtime entity IDs to Java entity IDs.
 * Updated by the ViaBedrock proxy via ENTITY_MAPPING payloads.
 * Thread-safe: payloads arrive on the network thread, queries happen on the render thread.
 */
public class EntityMappingStore {

    private static final EntityMappingStore INSTANCE = new EntityMappingStore();

    private static final byte OP_ADD = 0;
    private static final byte OP_REMOVE = 1;
    private static final byte OP_SYNC = 2;

    private final ConcurrentHashMap<Long, Integer> bedrockToJavaId = new ConcurrentHashMap<>();

    public static EntityMappingStore getInstance() {
        return INSTANCE;
    }

    /**
     * Translate a Bedrock runtime entity ID to a Java entity ID.
     * @return Java entity ID, or -1 if no mapping exists
     */
    public int getJavaEntityId(long bedrockRuntimeId) {
        return bedrockToJavaId.getOrDefault(bedrockRuntimeId, -1);
    }

    /**
     * Process an ENTITY_MAPPING payload from the proxy.
     */
    public void handlePayload(byte[] data) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.wrappedBuffer(data));
        try {
            byte operation = buf.readByte();
            switch (operation) {
                case OP_ADD -> {
                    long rid = buf.readLong();
                    int jid = buf.readInt();
                    bedrockToJavaId.put(rid, jid);
                }
                case OP_REMOVE -> {
                    long rid = buf.readLong();
                    bedrockToJavaId.remove(rid);
                }
                case OP_SYNC -> {
                    bedrockToJavaId.clear();
                    int count = buf.readInt();
                    for (int i = 0; i < count; i++) {
                        bedrockToJavaId.put(buf.readLong(), buf.readInt());
                    }
                    ModUIClient.LOGGER.info("[EntityMapping] Synced {} entity mappings", count);
                }
                default -> ModUIClient.LOGGER.warn("[EntityMapping] Unknown operation: {}", operation);
            }
        } finally {
            buf.release();
        }
    }

    /**
     * Clear all mappings (e.g. on disconnect).
     */
    public void clear() {
        bedrockToJavaId.clear();
    }
}
