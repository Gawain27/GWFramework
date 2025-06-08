package com.gwngames.core.asset;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.utils.Disposable;
import com.gwngames.core.api.asset.IAssetManager;
import com.gwngames.core.api.asset.IAssetSubType;
import com.gwngames.core.api.asset.IAssetSubTypeRegistry;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;
import com.gwngames.core.data.ModuleNames;

import java.io.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lazy-loading, time-cached asset manager.
 *
 * <ul>
 *   <li>Assets are scheduled & loaded on first request</li>
 *   <li>Every successful {@link #get(String, Class)} call “touches” the asset</li>
 *   <li>{@link #update(float)} must be invoked each frame (or periodically)
 *       – it drives asynchronous loading *and* evicts stale entries</li>
 *   <li>Eviction is safe because we unload only when LibGDX’s reference
 *       count is 1 (ours) <em>and</em> the TTL has expired</li>
 * </ul>
 */
@Init(module = ModuleNames.CORE)
public final class ModularAssetManager extends BaseComponent
    implements IAssetManager, Disposable {

    // ───────────────────────── configuration ──────────────────────────
    /** Milliseconds an asset may remain unused before it is evicted. */
    private static final long DEFAULT_TTL_MS =
        Long.getLong("gw.asset.ttl", 5 * 60_000); // 5 minutes by default

    private static final FileLogger LOG = FileLogger.get(LogFiles.ASSET);

    // ────────────────────────── DI & runtime state ────────────────────
    @Inject private IAssetSubTypeRegistry reg;

    /** LibGDX core manager (handles reference counting, async IO, etc.). */
    private final AssetManager gdx = new AssetManager();

    /** All paths discovered while parsing every assets.txt. */
    private final Map<String, IAssetSubType> discovered = new ConcurrentHashMap<>();

    /** Last “touch” timestamp [ms] for each loaded asset. */
    private final Map<String, Long> lastUsed = new ConcurrentHashMap<>();

    // ────────────────────────── lifecycle ─────────────────────────────
    public ModularAssetManager() { scanAllAssetsTxt(); }

    @Override public void dispose() { gdx.dispose(); }

    // ────────────────────────── public API ────────────────────────────
    @Override
    public <T> T get(String path, Class<T> as) {
        ensureScheduled(path, as);
        gdx.finishLoadingAsset(path);       // blocks only for this asset
        touch(path);
        return gdx.get(path, as);
    }

    @Override
    public IAssetSubType subtypeOf(String path) { return discovered.get(path); }

    /**
     * Drive asynchronous loading and evict assets whose TTL has expired.
     * Call this once per frame (or at a regular interval).
     *
     * @param delta seconds since last call – forwarded to {@link AssetManager#update(int)}
     * @return {@code true} if LibGDX has nothing left to load
     */
    public boolean update(float delta) {
        boolean done = gdx.update((int)(delta * 1000));
        evictStale();
        return done;
    }

    // ───────────────────────── internal helpers ───────────────────────
    private void ensureScheduled(String path, Class<?> as) {
        if (!gdx.isLoaded(path) && gdx.getReferenceCount(path) == 0) {
            gdx.load(path, as);
        }
    }

    private void touch(String path) { lastUsed.put(path, System.currentTimeMillis()); }

    /** Unload assets that have not been used for {@link #DEFAULT_TTL_MS}. */
    private void evictStale() {
        long now = System.currentTimeMillis();
        lastUsed.forEach((path, ts) -> {
            if (now - ts < DEFAULT_TTL_MS) return;          // still fresh
            if (gdx.getReferenceCount(path) > 1) return;    // someone else holds it
            gdx.unload(path);
            lastUsed.remove(path);
            LOG.debug("Evicted asset {}", path);
        });
    }

    /** Parse every module’s assets.txt – <em>no</em> loading is triggered here. */
    private void scanAllAssetsTxt() {
        for (ModuleClassLoader.ProjectLoader pl
            : ModuleClassLoader.getInstance().getClassLoaders()) {

            try (InputStream in = pl.cl().getResourceAsStream("assets.txt")) {
                if (in == null) continue;

                try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
                    br.lines()
                        .filter(s -> !s.isBlank())
                        .forEach(path -> discovered.put(
                            path,
                            reg.byExtension(extensionOf(path))));
                }

            } catch (IOException e) {
                LOG.error("assets.txt error in {}: {}", pl.cl(), e.toString());
            }
        }
        LOG.info("Discovered {} assets (lazy mode on)", discovered.size());
    }

    private static String extensionOf(String path) {
        int idx = path.lastIndexOf('.');
        return idx == -1 ? "" : path.substring(idx + 1);
    }
}
