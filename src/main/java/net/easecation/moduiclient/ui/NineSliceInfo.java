package net.easecation.moduiclient.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.easecation.moduiclient.ModUIClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.Resource;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Bedrock nine-slice texture definition.
 *
 * JSON format (sidecar file alongside .png):
 *   {"nineslice_size": 2, "base_size": [6, 163]}           — uniform inset
 *   {"nineslice_size": [8, 7, 8, 7], "base_size": [65, 24]} — [left, top, right, bottom]
 */
public class NineSliceInfo {

    private static final Map<String, NineSliceInfo> CACHE = new HashMap<>();
    private static final NineSliceInfo NONE = new NineSliceInfo(0, 0, 0, 0, 0, 0);

    public final int left, top, right, bottom;
    public final int baseWidth, baseHeight;

    private NineSliceInfo(int left, int top, int right, int bottom, int baseWidth, int baseHeight) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        this.baseWidth = baseWidth;
        this.baseHeight = baseHeight;
    }

    public boolean isNineSlice() {
        return left > 0 || top > 0 || right > 0 || bottom > 0;
    }

    /**
     * Try to load a nine-slice definition for the given texture path.
     * Looks for a .json file at the same path as the .png texture.
     * E.g. "textures/ui/bg.png" → "textures/ui/bg.json"
     *
     * @param texturePath Bedrock-style path (e.g. "textures/ui/bg" or "textures/ui/bg.png")
     * @return NineSliceInfo, or null if no nine-slice definition exists
     */
    public static NineSliceInfo forTexture(String texturePath) {
        if (texturePath == null || texturePath.trim().isEmpty()) return null;

        String normalizedPath = texturePath.toLowerCase(java.util.Locale.ROOT);
        if (normalizedPath.endsWith(".png")) {
            normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 4);
        } else if (normalizedPath.endsWith(".jpg")) {
            normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 4);
        } else if (normalizedPath.endsWith(".jpeg")) {
            normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 5);
        }

        String cacheKey = normalizedPath;
        if (CACHE.containsKey(cacheKey)) {
            NineSliceInfo cached = CACHE.get(cacheKey);
            return cached == NONE ? null : cached;
        }

        NineSliceInfo info = loadFromResource(normalizedPath);
        CACHE.put(cacheKey, info != null ? info : NONE);
        return info;
    }

    private static NineSliceInfo loadFromResource(String basePath) {
        String jsonPath = basePath + ".json";
        Identifier id = Identifier.of("minecraft", jsonPath);

        try {
            Optional<Resource> resourceOpt = MinecraftClient.getInstance()
                    .getResourceManager().getResource(id);
            if (resourceOpt.isEmpty()) return null;

            try (InputStream is = resourceOpt.get().getInputStream();
                 InputStreamReader reader = new InputStreamReader(is, StandardCharsets.UTF_8)) {
                JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
                return parseJson(json);
            }
        } catch (Exception e) {
            ModUIClient.LOGGER.debug("[NineSlice] Failed to load {}: {}", jsonPath, e.getMessage());
            return null;
        }
    }

    private static NineSliceInfo parseJson(JsonObject json) {
        if (!json.has("nineslice_size")) return null;

        int left, top, right, bottom;
        JsonElement sliceElem = json.get("nineslice_size");

        if (sliceElem.isJsonArray()) {
            JsonArray arr = sliceElem.getAsJsonArray();
            left = arr.get(0).getAsInt();
            top = arr.get(1).getAsInt();
            right = arr.get(2).getAsInt();
            bottom = arr.get(3).getAsInt();
        } else {
            int uniform = sliceElem.getAsInt();
            left = top = right = bottom = uniform;
        }

        int baseW = 0, baseH = 0;
        if (json.has("base_size")) {
            JsonArray base = json.getAsJsonArray("base_size");
            baseW = base.get(0).getAsInt();
            baseH = base.get(1).getAsInt();
        }

        return new NineSliceInfo(left, top, right, bottom, baseW, baseH);
    }

    public static void clearCache() {
        CACHE.clear();
    }
}
