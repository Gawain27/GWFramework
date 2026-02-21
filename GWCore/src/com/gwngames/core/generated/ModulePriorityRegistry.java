package com.gwngames.core.generated;

import com.gwngames.catalog.ModulePriorities;
import com.gwngames.core.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runtime module priority lookup.
 * This class is NOT generated.
 * It bootstraps by scanning the runtime classpath for classes annotated with {@link ModulePriorities}
 * and caching their entries.
 */
public final class ModulePriorityRegistry {
    private ModulePriorityRegistry() {}

    private static final Logger LOG = Logger.getLogger(ModulePriorityRegistry.class.getName());

    private static final ConcurrentHashMap<String, Integer> CACHE = new ConcurrentHashMap<>();
    private static volatile boolean initialized = false;

    /**
     * Returns priority for the given module id (case-insensitive).
     * Throws if unknown / not registered (strict).
     */
    public static int priorityOf(String moduleId) {
        if (StringUtils.isEmpty(moduleId)) {
            throw new IllegalStateException("Module id is null/empty");
        }
        ensureInit();

        String key = norm(moduleId);
        Integer p = CACHE.get(key);
        if (p == null) {
            // Log + throw (as requested)
            String known = String.join(", ", new TreeSet<>(CACHE.keySet()));
            LOG.severe(() -> "Unknown module id \"" + moduleId + "\". Known module ids: [" + known + "]");
            throw new IllegalStateException(
                "Unknown module id \"" + moduleId + "\" (normalized: \"" + key + "\"). " +
                    "No @ModulePriorities.Entry found for it. Known: [" + known + "]"
            );
        }
        return p;
    }

    /** Optional: force re-scan (useful in tests). */
    public static synchronized void reload() {
        initialized = false;
        CACHE.clear();
        ensureInit();
    }

    /** Snapshot of the registry (sorted, unmodifiable). */
    public static Map<String, Integer> asMap() {
        ensureInit();
        return Collections.unmodifiableMap(new TreeMap<>(CACHE));
    }

    // ------------------------------------------------------------

    private static void ensureInit() {
        if (initialized) return;
        synchronized (ModulePriorityRegistry.class) {
            if (initialized) return;

            long t0 = System.currentTimeMillis();
            LOG.info("ModulePriorityRegistry: initializing (scanning @ModulePriorities on classpath) ...");

            // Optional minimal defaults (remove if you want "annotations only" hard strictness)
            loadBuiltInDefaults();

            int annotatedClasses = 0;
            int entries = 0;

            try {
                ScanResult r = scanClasspathForModulePriorities();
                annotatedClasses = r.annotatedClasses;
                entries = r.entries;
            } catch (Throwable t) {
                LOG.log(Level.SEVERE, "ModulePriorityRegistry: classpath scan failed", t);
                // We keep defaults, but if you want hard-fail on scan issues:
                // throw new IllegalStateException("Failed to scan @ModulePriorities", t);
            }

            initialized = true;

            long dt = System.currentTimeMillis() - t0;
            int finalEntries = entries;
            int finalAnnotatedClasses = annotatedClasses;
            LOG.info(() ->
                "ModulePriorityRegistry: initialized. annotatedClasses=" + finalAnnotatedClasses +
                    ", entries=" + finalEntries +
                    ", cachedKeys=" + CACHE.size() +
                    ", timeMs=" + dt
            );
            if (LOG.isLoggable(Level.FINE)) {
                LOG.fine(() -> "ModulePriorityRegistry: map=" + new TreeMap<>(CACHE));
            }
        }
    }

    /** Minimal safe defaults so runtime never explodes early (optional). */
    private static void loadBuiltInDefaults() {
        CACHE.putIfAbsent("unimplemented", 0);
        CACHE.putIfAbsent("interface", 1);
        CACHE.putIfAbsent("core", 5);
    }

    private record ScanResult(int annotatedClasses, int entries) {}

    private static ScanResult scanClasspathForModulePriorities() throws IOException {
        String cp = System.getProperty("java.class.path", "");
        if (cp.isBlank()) {
            LOG.warning("ModulePriorityRegistry: java.class.path is blank; cannot scan. Using defaults only.");
            return new ScanResult(0, 0);
        }

        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = ModulePriorityRegistry.class.getClassLoader();

        int annotatedClasses = 0;
        int entries = 0;

        String[] parts = cp.split(File.pathSeparator);
        for (String p : parts) {
            if (p == null || p.isBlank()) continue;

            Path path = Paths.get(p);
            if (Files.notExists(path)) continue;

            if (Files.isDirectory(path)) {
                // scan directory of .class files
                ScanResult r = scanDirectory(path, cl);
                annotatedClasses += r.annotatedClasses;
                entries += r.entries;
            } else if (p.endsWith(".jar")) {
                // scan jar entries
                ScanResult r = scanJar(path.toFile(), cl);
                annotatedClasses += r.annotatedClasses;
                entries += r.entries;
            }
        }

        return new ScanResult(annotatedClasses, entries);
    }

    private static ScanResult scanJar(File jarFile, ClassLoader cl) {
        int annotatedClasses = 0;
        int entries = 0;

        try (JarFile jf = new JarFile(jarFile)) {
            Enumeration<JarEntry> it = jf.entries();
            while (it.hasMoreElements()) {
                JarEntry e = it.nextElement();
                if (e.isDirectory()) continue;
                String name = e.getName();
                if (!name.endsWith(".class")) continue;
                if (name.contains("module-info.class")) continue;

                String fqn = name.substring(0, name.length() - 6).replace('/', '.');
                ScanResult r = tryRegisterFromClassName(fqn, cl);
                annotatedClasses += r.annotatedClasses;
                entries += r.entries;
            }
        } catch (Throwable t) {
            LOG.log(Level.FINE, "ModulePriorityRegistry: failed scanning jar " + jarFile, t);
        }

        return new ScanResult(annotatedClasses, entries);
    }

    private static ScanResult scanDirectory(Path root, ClassLoader cl) throws IOException {
        int annotatedClasses = 0;
        int entries = 0;

        // Walk only .class files
        try (var stream = Files.walk(root)) {
            Iterator<Path> it = stream
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".class"))
                .iterator();

            int rootLen = root.toAbsolutePath().toString().length() + 1;

            while (it.hasNext()) {
                Path cls = it.next().toAbsolutePath();
                String abs = cls.toString();
                if (abs.endsWith("module-info.class")) continue;

                String rel = abs.substring(rootLen);
                String fqn = rel
                    .replace(File.separatorChar, '.')
                    .replaceAll("\\.class$", "");

                ScanResult r = tryRegisterFromClassName(fqn, cl);
                annotatedClasses += r.annotatedClasses;
                entries += r.entries;
            }
        }

        return new ScanResult(annotatedClasses, entries);
    }

    private static ScanResult tryRegisterFromClassName(String fqn, ClassLoader cl) {
        try {
            // Don't initialize class (avoid side effects)
            Class<?> c = Class.forName(fqn, false, cl);
            ModulePriorities mp = c.getAnnotation(ModulePriorities.class);
            if (mp == null) return new ScanResult(0, 0);

            int added = 0;
            for (ModulePriorities.Entry e : mp.value()) {
                String id = e.id();
                int pr = e.priority();
                if (StringUtils.isEmpty(id)) continue;

                String key = norm(id);
                Integer prev = CACHE.putIfAbsent(key, pr);
                if (prev != null && prev != pr) {
                    // Duplicate with different priority: log it loudly
                    LOG.severe(() ->
                        "ModulePriorityRegistry: conflicting priorities for id \"" + id +
                            "\" (key=\"" + key + "\"): " + prev + " vs " + pr +
                            " found on class " + c.getName()
                    );
                    // Choose: either throw hard, or keep first.
                    // You asked for strictness on missing modules; duplicates are also dangerous:
                    throw new IllegalStateException(
                        "Conflicting @ModulePriorities for module id \"" + id + "\": " + prev + " vs " + pr +
                            " (class=" + c.getName() + ")"
                    );
                }
                if (prev == null) added++;
            }

            LOG.fine(() -> "ModulePriorityRegistry: registered @ModulePriorities from " + c.getName());
            return new ScanResult(1, added);
        } catch (ClassNotFoundException ignored) {
            return new ScanResult(0, 0);
        } catch (LinkageError t) {
            // Lots of classes may not load cleanly in modular setups; keep scanning.
            LOG.log(Level.FINER, "ModulePriorityRegistry: cannot inspect " + fqn, t);
            return new ScanResult(0, 0);
        }
    }

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}
