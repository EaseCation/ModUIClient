package net.easecation.moduiclient.ui.texture;

import net.easecation.moduiclient.ModUIClient;
import net.easecation.moduiclient.ui.element.UIElementImage;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Manages URL-based texture downloads for image elements.
 *
 * Two-layer throttling to prevent render thread stalls:
 * 1. Bounded download thread pool (max 4 concurrent HTTP requests)
 * 2. Per-frame GPU registration limit (max 3 texture uploads per tick)
 *
 * All shared state is accessed only on the render thread.
 * Downloads run on a dedicated thread pool, touching no shared state.
 */
public class TextureUrlManager {

    private static final TextureUrlManager INSTANCE = new TextureUrlManager();
    private static final int MAX_CONCURRENT_DOWNLOADS = 4;
    private static final int MAX_REGISTRATIONS_PER_TICK = 3;

    /** Completed downloads: cacheKey → registered Identifier */
    private final Map<String, Identifier> cache = new HashMap<>();
    /** Keys with an in-flight download or pending registration (for deduplication) */
    private final Set<String> inFlight = new HashSet<>();
    /** Pending callbacks per cacheKey (multiple elements may request the same URL) */
    private final Map<String, List<Consumer<Identifier>>> callbacks = new HashMap<>();
    /** Downloaded images waiting for GPU registration (processed in tick()) */
    private final ArrayDeque<PendingRegistration> registrationQueue = new ArrayDeque<>();
    /** All dynamically registered texture Identifiers (for cleanup) */
    private final Set<Identifier> registeredTextures = new HashSet<>();
    /** Generation counter — incremented on cleanup to discard stale downloads */
    private int generation = 0;

    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(
            MAX_CONCURRENT_DOWNLOADS,
            r -> { Thread t = new Thread(r, "ModUI-Download"); t.setDaemon(true); return t; });

    private TextureUrlManager() {}

    public static TextureUrlManager getInstance() {
        return INSTANCE;
    }

    /**
     * Request a texture from a URL. Callback is invoked on the render thread
     * once the texture has been registered (may be delayed by tick throttling).
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

        // 2. Already downloading or in registration queue — just append callback
        callbacks.computeIfAbsent(cacheKey, k -> new ArrayList<>()).add(callback);
        if (inFlight.contains(cacheKey)) {
            return;
        }

        // 3. New download
        inFlight.add(cacheKey);
        int gen = this.generation;

        CompletableFuture
                .supplyAsync(() -> downloadImage(url), downloadExecutor)
                .thenAcceptAsync(nativeImage -> {
                    if (gen != generation) {
                        // Stale download from previous session — discard
                        nativeImage.close();
                        return;
                    }
                    registrationQueue.add(new PendingRegistration(cacheKey, nativeImage));
                }, MinecraftClient.getInstance()::execute)
                .exceptionally(throwable -> {
                    ModUIClient.LOGGER.warn("[TextureUrl] Failed to download {}: {}",
                            url, throwable.getMessage());
                    MinecraftClient.getInstance().execute(() -> {
                        if (gen != generation) return;
                        inFlight.remove(cacheKey);
                        List<Consumer<Identifier>> cbs = callbacks.remove(cacheKey);
                        if (cbs != null) {
                            for (var cb : cbs) cb.accept(null);
                        }
                    });
                    return null;
                });
    }

    /**
     * Process pending GPU registrations, called each frame from UIManager.renderHud().
     * Limits texture uploads per tick to prevent render thread stalls.
     */
    public void tick() {
        int processed = 0;
        while (!registrationQueue.isEmpty() && processed < MAX_REGISTRATIONS_PER_TICK) {
            PendingRegistration reg = registrationQueue.poll();
            Identifier id = registerTexture(reg.cacheKey, reg.image);
            inFlight.remove(reg.cacheKey);
            if (id != null) {
                cache.put(reg.cacheKey, id);
            }
            List<Consumer<Identifier>> cbs = callbacks.remove(reg.cacheKey);
            if (cbs != null) {
                for (var cb : cbs) cb.accept(id);
            }
            processed++;
        }
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
     * Called on disconnect/reset. Increments generation to discard stale downloads.
     */
    public void cleanup() {
        generation++;
        TextureManager tm = MinecraftClient.getInstance().getTextureManager();
        for (Identifier id : registeredTextures) {
            tm.destroyTexture(id);
        }
        registeredTextures.clear();
        cache.clear();
        inFlight.clear();
        callbacks.clear();
        // Free native memory held by queued NativeImages
        for (PendingRegistration reg : registrationQueue) {
            reg.image.close();
        }
        registrationQueue.clear();
    }

    /**
     * Sanitize a string for use as Identifier path.
     * Identifier paths only allow [a-z0-9/._-].
     */
    private static String sanitize(String name) {
        return name.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._/-]", "_");
    }

    private record PendingRegistration(String cacheKey, NativeImage image) {}
}
