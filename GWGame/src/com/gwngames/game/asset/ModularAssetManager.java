package com.gwngames.game.asset;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.resolvers.AbsoluteFileHandleResolver;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Disposable;
import com.gwngames.core.api.asset.IAssetManager;
import com.gwngames.core.api.asset.IAssetPath;
import com.gwngames.core.api.asset.IAssetSubType;
import com.gwngames.core.api.asset.IAssetSubTypeRegistry;
import com.gwngames.core.api.base.cfg.ILocale;
import com.gwngames.core.api.build.IPathResolver;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.build.PostInject;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.util.StringUtils;
import com.gwngames.game.data.asset.AssetCategory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.*;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Init(module = ModuleNames.CORE)
public final class ModularAssetManager extends BaseComponent implements IAssetManager, Disposable {

    // TODO: to config
    /** Idle-time before an unused asset is evicted (milliseconds). */
    private static volatile long TTL_MS = Long.getLong("gw.asset.ttl", 5 * 60_000);
    public static void setTtl(long millis) { TTL_MS = millis; }

    private static final FileLogger LOG = FileLogger.get(LogFiles.ASSET);

    @Inject
    private IAssetSubTypeRegistry reg;
    @Inject
    private ILocale locale;
    @Inject
    private IPathResolver paths;

    /** LibGDX manager using absolute paths. */
    private final AssetManager gdx = new AssetManager(new AbsoluteFileHandleResolver());

    /** Logical path -> subtype discovered via assets.txt (or lazily). */
    private final Map<String, IAssetSubType> discovered = new ConcurrentHashMap<>();
    /** ABS path -> last touch time. */
    private final Map<String, Long> lastUsed = new ConcurrentHashMap<>();
    /** logical -> ABS cache. */
    private final Map<String, String> absCache = new ConcurrentHashMap<>();

    /** Filesystem root for assets. */
    private Path assetsRoot;

    @PostInject
    private void init() {
        assetsRoot = paths.assetsDir();
        LOG.info("Assets root resolved to: {}", assetsRoot);

        scanAllAssetsTxt();

        // IMPORTANT: register a working loader for FileHandle
        gdx.setLoader(FileHandle.class, new FileHandleLoader(gdx.getFileHandleResolver()));
        LOG.debug("Registered FileHandleLoader for AssetManager");
    }

    @Override public void dispose() { gdx.dispose(); }

    // ───────────────────────── public API ─────────────────────────

    @Override
    public <T> T get(String path, Class<T> as) {
        final String abs = toAbsolute(path);

        if (!java.nio.file.Files.exists(java.nio.file.Path.of(abs))) {
            throw new IllegalArgumentException("Asset file not found on disk: " + abs);
        }

        ensureScheduled(abs, as);
        gdx.finishLoadingAsset(abs); // will block until that specific asset is loaded
        touch(abs);

        // DEBUG: assert loaded
        if (!gdx.isLoaded(abs)) {
            throw new com.badlogic.gdx.utils.GdxRuntimeException("Asset not loaded after finish: " + abs);
        }
        return gdx.get(abs, as);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T get(IAssetPath asset) {
        String rel = choosePath(asset); // e.g. css/dashboard-dark.css

        IAssetSubType st = discovered.get(rel);
        if (st == null) {
            String ext = StringUtils.extensionOf(rel);
            st = reg.byExtension(ext);
            if (st != null) {
                discovered.put(rel, st);
                LOG.debug("Subtype lazily resolved for '{}': {} -> {}", rel, ext, st.id());
            }
        }
        if (st == null) {
            LOG.debug("Unknown asset (not discovered + no subtype): '{}'", rel);
            throw new IllegalArgumentException("Unknown asset: " + rel);
        }

        String abs = toAbsolute(rel);
        Class<T> as = (Class<T>) st.libGdxClass();
        LOG.debug("Asset request -> enum={}, rel='{}', abs='{}', class={}",
            asset, rel, abs, as.getSimpleName());

        return get(rel, as);
    }

    @Override
    public <T> T get(IAssetPath asset, Class<T> as) {
        return get(choosePath(asset), as);
    }

    @Override
    public IAssetSubType subtypeOf(String path) {
        return discovered.get(toLogical(path));
    }

    @Override
    public boolean update(float delta) {
        boolean done = gdx.update((int)(delta * 1000));
        evictStale();
        return done;
    }

    // ───────────────────────── internals ─────────────────────────

    private void ensureScheduled(String absPath, Class<?> as) {
        if (gdx.isLoaded(absPath)) {
            LOG.debug("Already loaded: {}", absPath);
            return;
        }
        // Always (re)schedule when not loaded. If it was already queued,
        // AssetManager will simply increase ref-count and still finish correctly.
        LOG.debug("Scheduling load: {} ({})", absPath, as.getSimpleName());
        gdx.load(absPath, as);
    }

    private void touch(String absPath) {
        lastUsed.put(absPath, System.currentTimeMillis());
    }

    /** Unload assets that have not been used for {@link #TTL_MS}. */
    private void evictStale() {
        long now = System.currentTimeMillis();
        lastUsed.forEach((abs, ts) -> {
            long age = now - ts;
            if (age < TTL_MS) return;
            int refs = gdx.getReferenceCount(abs);
            if (refs > 1) return;
            gdx.unload(abs);
            lastUsed.remove(abs);
            LOG.debug("Evicted asset '{}' (age={}ms, refs={})", abs, age, refs);
        });
    }

    /**
     * Return a snapshot list of all discovered logical paths that belong to the given sub-type.
     * <p>
     * Notes:
     * <ul>
     *   <li>Results come from the {@code discovered} map (populated from assets.txt scans and
     *       any lazy discovery you do). If you need full filesystem coverage, call a scan that
     *       fills {@code discovered} first.</li>
     *   <li>Paths are logical (assets-root relative, forward slashes).</li>
     * </ul>
     */
    @Override
    public List<String> listAssets(IAssetSubType wanted) {
        if (wanted == null) return List.of();
        final String wantedId = wanted.id();
        return discovered.entrySet().stream()
            .filter(e -> {
                IAssetSubType st = e.getValue();
                // Be robust across enum/impl instances by comparing id()
                return st == wanted || (st != null && wantedId.equals(st.id()));
            })
            .map(Map.Entry::getKey)
            .sorted()
            .toList();
    }

    /**
     * Return all discovered logical paths in a given category (e.g., TEXTURE, AUDIO).
     * Handy when you don’t care about the specific sub-type (e.g., Texture vs Atlas).
     */
    @Override
    public List<String> listAssetsByCategory(AssetCategory category) {
        if (category == null) return List.of();
        return discovered.entrySet().stream()
            .filter(e -> {
                IAssetSubType st = e.getValue();
                return st != null && category.equals(st.category());
            })
            .map(Map.Entry::getKey)
            .sorted()
            .toList();
    }
    private void scanAllAssetsTxt() {
        discovered.clear();
        scanFromClasspathAssetsTxt();
        scanFromFilesystemAssetsTxt();
        LOG.info("Discovered {} assets (lazy mode on)", discovered.size());
    }

    private void scanFromClasspathAssetsTxt() {
        for (ModuleClassLoader.ProjectLoader pl : ModuleClassLoader.getInstance().getClassLoaders()) {
            try (InputStream in = pl.cl().getResourceAsStream("assets.txt")) {
                if (in == null) continue;
                try (BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
                    br.lines()
                        .map(String::trim)
                        .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                        .forEach(this::discoverPath);
                }
            } catch (IOException e) {
                LOG.error("assets.txt error in {}: {}", pl.cl(), e.toString());
            }
        }
    }

    private void scanFromFilesystemAssetsTxt() {
        try {
            Path file = paths.assetsDir().resolve("assets.txt");
            if (!Files.isRegularFile(file)) return;
            Files.lines(file)
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.startsWith("#"))
                .forEach(this::discoverPath);
        } catch (Exception e) {
            LOG.error("assets.txt filesystem scan failed: {}", e.toString());
        }
    }

    private void discoverPath(String relPath) {
        String ext = StringUtils.extensionOf(relPath);
        IAssetSubType st = reg.byExtension(ext);
        if (st == null) {
            LOG.debug("No subtype for '{}' (ext='{}')", relPath, ext);
            return;
        }
        // Avoid noisy duplicates
        if (discovered.putIfAbsent(relPath, st) == null) {
            LOG.debug("Discovered asset '{}' (ext='{}', subtype='{}' -> class={})",
                relPath, ext, st.id(), st.libGdxClass().getSimpleName());
        }
    }

    /** Locale-aware logical path selection. */
    private String choosePath(IAssetPath asset) {
        String locId = (locale != null && locale.getLocale() != null)
            ? locale.getLocale().toString() : null;

        if (locId != null) {
            String locPath = asset.path(locId);
            if (locPath != null && !locPath.equals(asset.path())) {
                return normalizeLogical(locPath);
            }
        }
        return normalizeLogical(asset.path());
    }

    private String normalizeLogical(String p) {
        return p.replace('\\', '/');
    }

    /** Convert logical or absolute to absolute path string (OS format). */
    @Override
    public String toAbsolute(String logicalOrAbsolute) {
        Path p = Paths.get(logicalOrAbsolute);
        if (p.isAbsolute()) return p.normalize().toString();

        String logical = normalizeLogical(logicalOrAbsolute);
        return absCache.computeIfAbsent(logical, l -> {
            String abs = assetsRoot.resolve(l).normalize().toString();
            LOG.debug("Asset path resolve: '{}' -> '{}'", l, abs);
            return abs;
        });
    }

    /** Convert absolute (under assetsRoot) to logical; otherwise return normalized logical. */
    private String toLogical(String path) {
        Path p = Paths.get(path).normalize();
        if (p.isAbsolute() && p.startsWith(assetsRoot)) {
            Path rel = assetsRoot.relativize(p);
            return normalizeLogical(rel.toString());
        }
        return normalizeLogical(path);
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        return cur;
    }
}
