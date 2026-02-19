package net.easecation.moduiclient.ui.texture;

import net.easecation.moduiclient.ModUIClient;
import net.easecation.moduiclient.ui.element.UIElementImage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Manages URL-based texture downloads for image elements.
 * Downloads happen on MC's download worker threads; texture registration
 * and all cache mutations happen on the render thread.
 */
public class TextureUrlManager {

    private static final TextureUrlManager INSTANCE = new TextureUrlManager();

    /** Completed downloads: cacheKey → registered Identifier */
    private final Map<String, Identifier> cache = new HashMap<>();
    /** In-flight downloads: cacheKey → Future (for deduplication) */
    private final Map<String, CompletableFuture<Identifier>> pending = new HashMap<>();
    /** All dynamically registered texture Identifiers (for cleanup) */
    private final Set<Identifier> registeredTextures = new HashSet<>();

    private TextureUrlManager() {}

    public static TextureUrlManager getInstance() {
        return INSTANCE;
    }

    /**
     * Request a texture from a URL. Callback is invoked on the render thread.
     *
     * @param url      image URL
     * @param cacheKey cache key (filename extracted from URL)
     * @param callback receives the Identifier on success, null on failure
     */
    public void requestTexture(String url, String cacheKey, Consumer<Identifier> callback) {
        // 1. Cache hit — immediate callback
        Identifier cached = cache.get(cacheKey);
        if (cached != null) {
            callback.accept(cached);
            return;
        }

        // 2. Already downloading — chain callback onto existing future
        CompletableFuture<Identifier> existingFuture = pending.get(cacheKey);
        if (existingFuture != null) {
            existingFuture.thenAcceptAsync(callback, MinecraftClient.getInstance()::execute);
            return;
        }

        // 3. New download
        CompletableFuture<Identifier> future = CompletableFuture
                .supplyAsync(() -> downloadImage(url), Util.getDownloadWorkerExecutor())
                .thenApplyAsync(
                        nativeImage -> registerTexture(cacheKey, nativeImage),
                        MinecraftClient.getInstance()::execute
                );

        future.thenAcceptAsync(id -> {
            pending.remove(cacheKey);
            if (id != null) {
                cache.put(cacheKey, id);
            }
            callback.accept(id);
        }, MinecraftClient.getInstance()::execute);

        future.exceptionally(throwable -> {
            ModUIClient.LOGGER.warn("[TextureUrl] Failed to download {}: {}", url, throwable.getMessage());
            MinecraftClient.getInstance().execute(() -> {
                pending.remove(cacheKey);
                callback.accept(null);
            });
            return null;
        });

        pending.put(cacheKey, future);
    }

    /**
     * Download image bytes and decode into NativeImage.
     * Runs on a download worker thread — must not touch any shared state.
     */
    private static NativeImage downloadImage(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(30_000);
            conn.setDoInput(true);
            conn.setRequestProperty("User-Agent", "ModUIClient/1.0");
            conn.connect();
            try {
                int code = conn.getResponseCode();
                if (code != 200) {
                    throw new RuntimeException("HTTP " + code + " for " + url);
                }
                byte[] bytes = conn.getInputStream().readAllBytes();
                return NativeImage.read(bytes);
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            throw new RuntimeException("Download failed: " + url, e);
        }
    }

    /**
     * Register a NativeImage as a dynamic texture. Runs on the render thread.
     */
    private Identifier registerTexture(String cacheKey, NativeImage image) {
        if (image == null) return null;
        Identifier id = Identifier.of("moduiclient", "url_tex/" + sanitize(cacheKey));
        NativeImageBackedTexture tex = new NativeImageBackedTexture(
                () -> id.toString(), image);
        MinecraftClient.getInstance().getTextureManager().registerTexture(id, tex);
        registeredTextures.add(id);
        // Cache texture dimensions for UV sub-region rendering
        UIElementImage.cacheTextureDimension(id, image.getWidth(), image.getHeight());
        ModUIClient.LOGGER.info("[TextureUrl] Registered: {} ({}x{})", id, image.getWidth(), image.getHeight());
        return id;
    }

    /**
     * Extract a cache key (filename) from a URL, matching Python behavior.
     * Replaces ? and & with _, appends .png if no extension.
     */
    public static String extractCacheKey(String url) {
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        fileName = fileName.replace('?', '_').replace('&', '_');
        if (!fileName.contains(".")) {
            fileName += ".png";
        }
        return fileName;
    }

    /**
     * Destroy all dynamically registered textures and clear caches.
     * Called on disconnect/reset.
     */
    public void cleanup() {
        TextureManager tm = MinecraftClient.getInstance().getTextureManager();
        for (Identifier id : registeredTextures) {
            tm.destroyTexture(id);
        }
        registeredTextures.clear();
        cache.clear();
        pending.clear();
    }

    /**
     * Sanitize a string for use as Identifier path.
     * Identifier paths only allow [a-z0-9/._-].
     */
    private static String sanitize(String name) {
        return name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._/-]", "_");
    }
}
