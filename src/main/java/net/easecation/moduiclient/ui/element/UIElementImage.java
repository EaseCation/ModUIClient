package net.easecation.moduiclient.ui.element;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.easecation.moduiclient.ui.NineSliceInfo;
import net.easecation.moduiclient.ui.texture.TextureUrlManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Image element supporting solid color fill, texture rendering, and nine-slice.
 */
public class UIElementImage extends UIElement {

    // Cache: texture Identifier → [width, height] from PNG header
    private static final Map<Identifier, int[]> TEXTURE_DIMS = new HashMap<>();

    private String texturePath;
    private Identifier textureId;
    private boolean textureExplicitlyEmpty = false; // textures="" → fully transparent
    private float colorR = 1f, colorG = 1f, colorB = 1f;
    private boolean hasColor = false;

    // UV mapping
    private float uvX = 0, uvY = 0;
    private float uvWidth = -1, uvHeight = -1; // -1 means use full texture

    // Rotation
    private float rotateAngle = 0;
    private float rotatePivotX = 0.5f, rotatePivotY = 0.5f;

    // Sequence animation (sprite sheet)
    private boolean hasSequence = false;
    private float seqFrameW, seqFrameH;   // uvSize: size of each frame
    private float seqSheetW, seqSheetH;   // uvLength: total sprite sheet size
    private float seqInterval;             // seconds per frame
    private boolean seqLoop;
    private long seqStartTime;

    // Nine-slice (lazily loaded from sidecar .json)
    private NineSliceInfo nineSlice;
    private boolean nineSliceLoaded = false;

    public UIElementImage(String name, String type) {
        super(name, type);
    }

    @Override
    public void initFromJson(JsonObject json) {
        super.initFromJson(json);

        // Texture path
        if (json.has("textures")) {
            texturePath = json.get("textures").getAsString();
            textureId = resolveTexture(texturePath);
            textureExplicitlyEmpty = (texturePath != null && texturePath.trim().isEmpty());
        }

        // Color tint
        if (json.has("color")) {
            JsonArray color = json.getAsJsonArray("color");
            if (color.size() >= 3) {
                colorR = color.get(0).getAsFloat();
                colorG = color.get(1).getAsFloat();
                colorB = color.get(2).getAsFloat();
                hasColor = true;
            }
        }

        // UV
        if (json.has("uv")) {
            JsonArray uv = json.getAsJsonArray("uv");
            if (uv.size() >= 2) {
                uvX = uv.get(0).getAsFloat();
                uvY = uv.get(1).getAsFloat();
            }
        }
        if (json.has("uvSize")) {
            JsonArray uvSize = json.getAsJsonArray("uvSize");
            if (uvSize.size() >= 2) {
                uvWidth = uvSize.get(0).getAsFloat();
                uvHeight = uvSize.get(1).getAsFloat();
            }
        }

        // Rotation
        if (json.has("rotateAngle")) {
            rotateAngle = json.get("rotateAngle").getAsFloat();
        }
        if (json.has("rotatePivot")) {
            JsonArray pivot = json.getAsJsonArray("rotatePivot");
            if (pivot.size() >= 2) {
                rotatePivotX = pivot.get(0).getAsFloat();
                rotatePivotY = pivot.get(1).getAsFloat();
            }
        }

        // URL texture (takes priority over textures if both present)
        if (json.has("textureUrl")) {
            String url = json.get("textureUrl").getAsString();
            String cacheKey;
            if (json.has("textureTmpFile")) {
                cacheKey = json.get("textureTmpFile").getAsString();
            } else {
                cacheKey = TextureUrlManager.extractCacheKey(url);
            }
            setTextureUrl(url, cacheKey);
        }

        // Sequence animation (sprite sheet)
        if (json.has("sequence")) {
            JsonObject seq = json.getAsJsonObject("sequence");
            if (seq.has("uvSize") && seq.has("uvLength")) {
                JsonArray uvSize = seq.getAsJsonArray("uvSize");
                JsonArray uvLength = seq.getAsJsonArray("uvLength");
                seqFrameW = uvSize.get(0).getAsFloat();
                seqFrameH = uvSize.get(1).getAsFloat();
                seqSheetW = uvLength.get(0).getAsFloat();
                seqSheetH = uvLength.get(1).getAsFloat();
                seqInterval = seq.has("interval") ? seq.get("interval").getAsFloat() : 0.1f;
                seqLoop = !seq.has("loop") || seq.get("loop").getAsBoolean();
                hasSequence = true;
                seqStartTime = System.currentTimeMillis();
            }
        }
    }

    private NineSliceInfo getNineSlice() {
        if (!nineSliceLoaded) {
            nineSlice = NineSliceInfo.forTexture(texturePath);
            nineSliceLoaded = true;
        }
        return nineSlice;
    }

    @Override
    public void render(DrawContext context, float tickDelta) {
        if (!isVisible() || getAlpha() <= 0) return;

        int x = (int) getResolvedX();
        int y = (int) getResolvedY();
        int w = (int) getResolvedWidth();
        int h = (int) getResolvedHeight();

        if (w <= 0 || h <= 0) return;

        // Apply rotation if needed
        boolean rotated = rotateAngle != 0;
        if (rotated) {
            context.getMatrices().pushMatrix();
            float pivotX = x + w * rotatePivotX;
            float pivotY = y + h * rotatePivotY;
            context.getMatrices().translate(pivotX, pivotY);
            context.getMatrices().rotate((float) Math.toRadians(rotateAngle));
            context.getMatrices().translate(-pivotX, -pivotY);
        }

        if (textureExplicitlyEmpty) {
            // textures="" → fully transparent, render nothing
        } else if (textureId != null) {
            int alpha = (int) (getAlpha() * 255);
            int r = (int) (colorR * 255);
            int g = (int) (colorG * 255);
            int b = (int) (colorB * 255);
            int color = (alpha << 24) | (r << 16) | (g << 8) | b;

            NineSliceInfo ns = getNineSlice();
            if (ns != null && ns.isNineSlice() && ns.baseWidth > 0 && ns.baseHeight > 0) {
                renderNineSliceStatic(context, textureId, x, y, w, h, ns, color);
            } else if ("imageElongate".equals(getType())) {
                // Aspect-ratio-preserving fit (like CSS object-fit: contain).
                // Scales to fill max area while keeping original proportions; centers with letterbox/pillarbox.
                int[] dims = queryTextureDimensions(textureId);
                if (dims != null && dims[0] > 0 && dims[1] > 0) {
                    float scale = Math.min((float) w / dims[0], (float) h / dims[1]);
                    int drawW = (int) (dims[0] * scale);
                    int drawH = (int) (dims[1] * scale);
                    int drawX = x + (w - drawW) / 2;
                    int drawY = y + (h - drawH) / 2;
                    context.drawTexture(RenderPipelines.GUI_TEXTURED, textureId,
                            drawX, drawY, 0, 0, drawW, drawH, drawW, drawH, color);
                } else {
                    // Dimensions unknown — fallback to stretch
                    context.drawTexture(RenderPipelines.GUI_TEXTURED, textureId,
                            x, y, 0, 0, w, h, w, h, color);
                }
            } else if (hasSequence) {
                // Sprite sheet animation: calculate current frame UV
                float elapsed = (System.currentTimeMillis() - seqStartTime) / 1000f;
                int framesPerRow = (int) (seqSheetW / seqFrameW);
                int framesPerCol = (int) (seqSheetH / seqFrameH);
                int totalFrames = framesPerRow * framesPerCol;
                if (totalFrames <= 0) totalFrames = 1;

                int frameIndex = (int) (elapsed / seqInterval);
                if (seqLoop) {
                    frameIndex = frameIndex % totalFrames;
                } else {
                    frameIndex = Math.min(frameIndex, totalFrames - 1);
                }

                int frameCol = frameIndex % framesPerRow;
                int frameRow = frameIndex / framesPerRow;
                float frameU = frameCol * seqFrameW;
                float frameV = frameRow * seqFrameH;

                // Draw current frame with separate screen size and UV region size
                context.drawTexture(RenderPipelines.GUI_TEXTURED, textureId,
                        x, y, frameU, frameV, w, h,
                        (int) seqFrameW, (int) seqFrameH,
                        (int) seqSheetW, (int) seqSheetH, color);
            } else if (uvWidth > 0 && uvHeight > 0) {
                // Custom UV sub-region: separate screen size from UV region
                int[] dims = queryTextureDimensions(textureId);
                if (dims != null) {
                    context.drawTexture(RenderPipelines.GUI_TEXTURED, textureId,
                            x, y, uvX, uvY, w, h,
                            (int) uvWidth, (int) uvHeight,
                            dims[0], dims[1], color);
                } else {
                    // Fallback: render full texture
                    context.drawTexture(RenderPipelines.GUI_TEXTURED, textureId,
                            x, y, 0, 0, w, h, w, h, color);
                }
            } else {
                // Full texture
                context.drawTexture(RenderPipelines.GUI_TEXTURED, textureId,
                        x, y, 0, 0, w, h, w, h, color);
            }
        } else if (hasColor) {
            // Solid color fill
            int alpha = (int) (getAlpha() * 255);
            int r = (int) (colorR * 255);
            int g = (int) (colorG * 255);
            int b = (int) (colorB * 255);
            int color = (alpha << 24) | (r << 16) | (g << 8) | b;
            context.fill(x, y, x + w, y + h, color);
        }

        if (rotated) {
            context.getMatrices().popMatrix();
        }
    }

    /**
     * Render a nine-slice texture.
     * Splits the texture into 9 regions based on insets and renders each region,
     * stretching the center and edge regions to fill the target area.
     *
     * Texture regions:
     *   TL | TC | TR      (top-left, top-center, top-right)
     *   ML | MC | MR      (middle-left, middle-center, middle-right)
     *   BL | BC | BR      (bottom-left, bottom-center, bottom-right)
     */
    static void renderNineSliceStatic(DrawContext ctx, Identifier texture,
                                       int x, int y, int w, int h,
                                       NineSliceInfo ns, int color) {
        int texW = ns.baseWidth;
        int texH = ns.baseHeight;
        int sl = ns.left;
        int st = ns.top;
        int sr = ns.right;
        int sb = ns.bottom;

        // Clamp insets if element is too small
        if (sl + sr > w) { sl = w / 2; sr = w - sl; }
        if (st + sb > h) { st = h / 2; sb = h - st; }

        int centerW = w - sl - sr;
        int centerH = h - st - sb;
        int texCenterW = texW - ns.left - ns.right;
        int texCenterH = texH - ns.top - ns.bottom;

        // Top row
        if (st > 0 && sl > 0)
            drawSlice(ctx, texture, x, y, sl, st, 0, 0, ns.left, ns.top, texW, texH, color);
        if (st > 0 && centerW > 0 && texCenterW > 0)
            drawSlice(ctx, texture, x + sl, y, centerW, st, ns.left, 0, texCenterW, ns.top, texW, texH, color);
        if (st > 0 && sr > 0)
            drawSlice(ctx, texture, x + sl + centerW, y, sr, st, texW - ns.right, 0, ns.right, ns.top, texW, texH, color);

        // Middle row
        if (centerH > 0 && sl > 0)
            drawSlice(ctx, texture, x, y + st, sl, centerH, 0, ns.top, ns.left, texCenterH, texW, texH, color);
        if (centerH > 0 && centerW > 0 && texCenterW > 0 && texCenterH > 0)
            drawSlice(ctx, texture, x + sl, y + st, centerW, centerH, ns.left, ns.top, texCenterW, texCenterH, texW, texH, color);
        if (centerH > 0 && sr > 0)
            drawSlice(ctx, texture, x + sl + centerW, y + st, sr, centerH, texW - ns.right, ns.top, ns.right, texCenterH, texW, texH, color);

        // Bottom row
        if (sb > 0 && sl > 0)
            drawSlice(ctx, texture, x, y + st + centerH, sl, sb, 0, texH - ns.bottom, ns.left, ns.bottom, texW, texH, color);
        if (sb > 0 && centerW > 0 && texCenterW > 0)
            drawSlice(ctx, texture, x + sl, y + st + centerH, centerW, sb, ns.left, texH - ns.bottom, texCenterW, ns.bottom, texW, texH, color);
        if (sb > 0 && sr > 0)
            drawSlice(ctx, texture, x + sl + centerW, y + st + centerH, sr, sb, texW - ns.right, texH - ns.bottom, ns.right, ns.bottom, texW, texH, color);
    }

    /**
     * Draw a single slice of the nine-slice texture.
     * Maps a region of the source texture (u,v,srcW,srcH) to a destination rectangle (x,y,dstW,dstH).
     */
    private static void drawSlice(DrawContext ctx, Identifier texture,
                                   int x, int y, int dstW, int dstH,
                                   int u, int v, int srcW, int srcH,
                                   int textureWidth, int textureHeight, int color) {
        // drawTexture(pipeline, id, x, y, u, v, width, height, textureWidth, textureHeight, color)
        // maps UV from (u, v) in a texture of size (textureWidth, textureHeight)
        // We need to scale the UV region to map srcW×srcH source pixels to dstW×dstH screen pixels
        // drawTexture draws a dstW×dstH rectangle and samples textureWidth×textureHeight pixels starting at (u,v)
        // So we set textureWidth/Height to scale the UV mapping correctly
        float scaleX = (float) dstW / srcW;
        float scaleY = (float) dstH / srcH;
        int scaledTexW = (int) (textureWidth * scaleX);
        int scaledTexH = (int) (textureHeight * scaleY);
        float scaledU = u * scaleX;
        float scaledV = v * scaleY;
        ctx.drawTexture(RenderPipelines.GUI_TEXTURED, texture, x, y, scaledU, scaledV,
                dstW, dstH, scaledTexW, scaledTexH, color);
    }

    private static Identifier resolveTexture(String bedrockPath) {
        if (bedrockPath == null || bedrockPath.trim().isEmpty()) return null;
        String path = bedrockPath.toLowerCase(java.util.Locale.ROOT);
        // Strip .jpg/.jpeg extension (ViaBedrock converts them to .png)
        if (path.endsWith(".jpg")) {
            path = path.substring(0, path.length() - 4) + ".png";
        } else if (path.endsWith(".jpeg")) {
            path = path.substring(0, path.length() - 5) + ".png";
        } else if (!path.endsWith(".png")) {
            path = path + ".png";
        }
        return Identifier.of("minecraft", path);
    }

    // --- Setters for commands ---

    public void setTexturePath(String path) {
        this.texturePath = path;
        this.textureId = resolveTexture(path);
        this.textureExplicitlyEmpty = (path != null && path.trim().isEmpty());
        this.nineSliceLoaded = false; // reset for lazy reload
        this.nineSlice = null;
    }

    /**
     * Set texture from a URL (async download).
     * The element renders nothing until download completes, then textureId is set.
     */
    public void setTextureUrl(String url, String cacheKey) {
        this.texturePath = null;
        this.textureExplicitlyEmpty = false;
        this.nineSliceLoaded = false;
        this.nineSlice = null;

        TextureUrlManager.getInstance().requestTexture(url, cacheKey, id -> {
            if (id != null) {
                this.textureId = id;
            }
        });
    }

    public void setColor(float r, float g, float b) {
        this.colorR = r;
        this.colorG = g;
        this.colorB = b;
        this.hasColor = true;
    }

    public String getTexturePath() { return texturePath; }
    public float getRotateAngle() { return rotateAngle; }
    public void setRotateAngle(float angle) { this.rotateAngle = angle; }
    public void setRotatePivot(float x, float y) { this.rotatePivotX = x; this.rotatePivotY = y; }
    public void setUV(float x, float y) { this.uvX = x; this.uvY = y; }
    public void setUVSize(float w, float h) { this.uvWidth = w; this.uvHeight = h; }

    // --- Texture dimension cache ---

    /**
     * Query actual texture pixel dimensions by reading the PNG IHDR header (24 bytes).
     * Results are cached per Identifier for zero-cost subsequent lookups.
     */
    private static int[] queryTextureDimensions(Identifier id) {
        if (id == null) return null;
        int[] cached = TEXTURE_DIMS.get(id);
        if (cached != null) return cached;

        try {
            var resource = MinecraftClient.getInstance().getResourceManager().getResource(id);
            if (resource.isPresent()) {
                try (InputStream is = resource.get().getInputStream()) {
                    byte[] header = new byte[24];
                    if (is.read(header) >= 24) {
                        // PNG IHDR: width at offset 16, height at offset 20 (big-endian)
                        int w = ((header[16] & 0xFF) << 24) | ((header[17] & 0xFF) << 16)
                                | ((header[18] & 0xFF) << 8) | (header[19] & 0xFF);
                        int h = ((header[20] & 0xFF) << 24) | ((header[21] & 0xFF) << 16)
                                | ((header[22] & 0xFF) << 8) | (header[23] & 0xFF);
                        if (w > 0 && h > 0) {
                            cached = new int[]{w, h};
                            TEXTURE_DIMS.put(id, cached);
                            return cached;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return null;
    }

    public static void cacheTextureDimension(Identifier id, int w, int h) {
        TEXTURE_DIMS.put(id, new int[]{w, h});
    }

    public static void clearTextureDimensionCache() {
        TEXTURE_DIMS.clear();
    }
}
