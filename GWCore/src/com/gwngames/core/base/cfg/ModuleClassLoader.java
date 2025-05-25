package com.gwngames.core.base.cfg;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.ex.ErrorPopupException;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.*;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Class‑loader responsible for dynamically loading the game’s pluggable modules.
 * <p>
 *   Workflow:
 *   <ol>
 *     <li>Reads <code>config.json</code> from the <em>bin</em> directory produced by the Gradle build.</li>
 *     <li>Instantiates a dedicated {@link URLClassLoader} for every module JAR listed therein.</li>
 *     <li>Exposes helper methods that locate and/or instantiate classes based on:
 *       <ul>
 *         <li>{@link ComponentNames} – the legacy mechanism (kept for full backward‑compatibility).</li>
 *         <li>A new <em>interface‑first</em> contract:<br>
 *         Each interface is annotated <pre>@Init(module = ModuleNames.INTERFACE, …)</pre> and every concrete
 *         implementation simply declares <pre>@Init(module = ModuleNames.&lt;SOME_MODULE&gt;)</pre>.</li>
 *       </ul>
 *       The implementation with the highest <code>modulePriority</code> wins.</li>
 *   </ol>
 *   All API signatures remain unchanged, so no calling‑code has to be updated.
 * </p>
 */
public class ModuleClassLoader extends ClassLoader {

    /* ───────────────────────── constants & singletons ───────────────────────── */
    private static final FileLogger log = FileLogger.get(LogFiles.SYSTEM);
    private static final List<Class<?>> buildComponents = new ArrayList<>();
    private static ModuleClassLoader instance;

    /* ────────────────────────────  per‑instance fields ───────────────────────── */
    private final List<URLClassLoader> orderedLoaders = new ArrayList<>();
    private final Map<URLClassLoader, JarFile> orderedJars = new LinkedHashMap<>();
    private final Map<String, Class<?>> resolved  = new ConcurrentHashMap<>();
    private final Set<String> notFound  = ConcurrentHashMap.newKeySet();

    /* ========================================================================== */
    /*  Construction                                                             */
    /* ========================================================================== */
    private ModuleClassLoader() throws ErrorPopupException {
        super(ModuleClassLoader.class.getClassLoader());
        initLoaders();
        initJars();
    }

    /** Singleton accessor. */
    public static synchronized ModuleClassLoader getInstance() {
        if (instance == null) {
            try {
                instance = new ModuleClassLoader();
            } catch (ErrorPopupException e) {
                throw new RuntimeException(e);
            }
        }
        return instance;
    }

    /* ========================================================================== */
    /*  Loader & JAR discovery                                                   */
    /* ========================================================================== */

    /* ==================================================================
     *  Helper: build all URLClassLoaders from a given <bin> directory
     * =================================================================*/
    private void initLoadersFromBinDir(File binDir) throws ErrorPopupException {
        log.debug("initLoadersFromBinDir({})", binDir.getAbsolutePath());

        /* A) Parse  <bin>/config.json --------------------------------- */
        File cfgFile = new File(binDir, "config.json");
        Map<String, Object> cfg;
        try (JsonReader reader = new JsonReader(new FileReader(cfgFile))) {
            Gson gson = new Gson();
            Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
            cfg = gson.fromJson(reader, mapType);
        } catch (IOException | JsonParseException e) {
            log.error("Failed to read config.json", e);
            throw new ErrorPopupException("Cannot read config.json");
        }

        String gameType = (String) cfg.get("gameType");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> projects = (List<Map<String, Object>>) cfg.get("projects");
        if (projects == null || projects.isEmpty())
            throw new ErrorPopupException("No projects defined in config.json!");
        log.log("gameType: {}, projects: {}", gameType, projects.size());

        /* B) Create URLClassLoaders in priority order ----------------- */
        File rootDir = binDir.getParentFile();             // parent of bin & lib
        List<ProjectLoader> tmp = new ArrayList<>();

        for (Map<String, Object> p : projects) {
            String jarRel = (String) p.get("jar");
            Number lvl    = (Number) p.get("level");
            String type   = (String) p.get("type");

            log.debug("candidate  jar:{} level:{} type:{}", jarRel, lvl, type);

            if (!gameType.equals(type) || jarRel == null || lvl == null) {
                log.debug("skipped");
                continue;
            }

            File jarFile;
            try {
                jarFile = new File(rootDir, jarRel).getCanonicalFile();
            } catch (IOException e) {
                throw new ErrorPopupException("Could not lookup jar: " + jarRel);
            }
            if (!jarFile.exists()) {
                log.error("JAR not found: {}", jarFile);
                continue;
            }

            URLClassLoader cl;
            try {
                cl = createNamedLoader(jarFile);
            } catch (MalformedURLException e) {
                throw new ErrorPopupException("Cannot create class loader: " + jarFile.getName());
            }
            tmp.add(new ProjectLoader(cl, lvl.intValue()));
            log.log("loader '{}' (level {})", cl.getName(), lvl);
        }

        tmp.sort(Comparator.comparingInt(pl -> -pl.level));      // high → low
        tmp.forEach(pl -> orderedLoaders.add(pl.classLoader));

        log.log("Loader order:");
        for (URLClassLoader l : orderedLoaders)
            log.log("{}  URLs:{}", l.getName(), Arrays.toString(l.getURLs()));
    }

    /**
     * Create and order the module class‑loaders according to the
     * <code>level</code> field in <code>config.json</code> (descending).
     */
    @SuppressWarnings("unchecked")
    private void initLoaders() throws ErrorPopupException {
        log.debug("initLoaders() - locating bins & reading config");

        /* 0)  CI / test override ------------------------------------------------ */
        String forcedBin = System.getProperty("gw.bin.dir");
        if (forcedBin != null) {
            File binDir = new File(forcedBin);
            if (!binDir.isDirectory())
                throw new ErrorPopupException("Override gw.bin.dir is not a directory: " + forcedBin);
            initLoadersFromBinDir(binDir);   // <- factor existing logic into helper
            return;
        }

        /* 1) Determine executable directory. */
        File whereAmI;
        try {
            URL src = getClass().getProtectionDomain().getCodeSource().getLocation();
            whereAmI = new File(src.toURI());
            if (whereAmI.isFile()) whereAmI = whereAmI.getParentFile();
            log.debug("Executable dir: {}", whereAmI);
        } catch (Exception ex) {
            log.error("Error getting executable", ex);
            throw new ErrorPopupException("Cannot determine executable location");
        }

        /* 2) Locate the <bin> directory. */
        File binDir;
        if (new File(whereAmI, "config.json").isFile()) {
            binDir = whereAmI;
        } else if ("lib".equals(whereAmI.getName())) {
            binDir = new File(whereAmI.getParentFile(), "bin");
            if (!new File(binDir, "config.json").isFile())
                throw new ErrorPopupException("config.json not found beside launcher");
        } else {
            throw new ErrorPopupException("Cannot locate bin directory (looked in " + whereAmI + ")");
        }
        log.debug("binDir: {}", binDir);

        /* 3) Parse config.json. */
        File cfgFile = new File(binDir, "config.json");
        Map<String, Object> cfg;
        try (JsonReader reader = new JsonReader(new FileReader(cfgFile))) {
            Gson gson = new Gson();
            Type mapType = new TypeToken<Map<String, Object>>(){}.getType();
            cfg = gson.fromJson(reader, mapType);
        } catch (IOException | JsonParseException e) {
            log.error("Failed to read config.json", e);
            throw new ErrorPopupException("Cannot read config.json");
        }

        String gameType = (String) cfg.get("gameType");
        List<Map<String,Object>> projects = (List<Map<String,Object>>) cfg.get("projects");
        if (projects == null || projects.isEmpty())
            throw new ErrorPopupException("No projects defined in config.json!");
        log.log("gameType: {}, projects: {}", gameType, projects.size());

        /* 4) Build and order class‑loaders. */
        File rootDir = binDir.getParentFile(); // parent of bin & lib
        List<ProjectLoader> tmp = new ArrayList<>();

        for (Map<String,Object> p : projects) {
            String jarRel = (String) p.get("jar");
            Number lvl    = (Number) p.get("level");
            String type   = (String) p.get("type");

            log.debug("candidate  jar:{} level:{} type:{}", jarRel, lvl, type);

            if (!gameType.equals(type) || jarRel == null || lvl == null) {
                log.debug("skipped");
                continue;
            }

            File jarFile;
            try {
                jarFile = new File(rootDir, jarRel).getCanonicalFile();
            } catch (IOException e) {
                throw new ErrorPopupException("Could not lookup jar: " + jarRel);
            }
            if (!jarFile.exists()) {
                log.error("JAR not found: {}", jarFile);
                continue;
            }

            URLClassLoader cl;
            try {
                cl = createNamedLoader(jarFile);
            } catch (MalformedURLException e) {
                throw new ErrorPopupException("Cannot create class loader: " + jarFile.getName());
            }
            tmp.add(new ProjectLoader(cl, lvl.intValue()));
            log.log("loader '{}' (level {})", cl.getName(), lvl);
        }

        tmp.sort(Comparator.comparingInt(pl -> -pl.level)); // high → low
        tmp.forEach(pl -> orderedLoaders.add(pl.classLoader));

        log.log("Loader order:");
        for (URLClassLoader l : orderedLoaders)
            log.log("{}  URLs:{}", l.getName(), Arrays.toString(l.getURLs()));
    }

    /**
     * Creates a {@link URLClassLoader} that exposes a meaningful name via
     * <code>getName()</code> (available since Java 9). Falls back gracefully on
     * Java 8 where unnamed loaders are unavoidable.
     */
    private URLClassLoader createNamedLoader(File jarFile) throws MalformedURLException {
        URL url = jarFile.toURI().toURL();
        try {
            // Java 9+ constructor with explicit name
            return URLClassLoader.class
                .getConstructor(String.class, URL[].class, ClassLoader.class)
                .newInstance(jarFile.getName(), new URL[]{url}, ModuleClassLoader.this);
        } catch (ReflectiveOperationException ignored) {
            // Java 8 – no named loaders, live with it
            URLClassLoader cl = new URLClassLoader(new URL[]{url}, ModuleClassLoader.this);
            log.debug("Unnamed loader created for {}", jarFile.getName());
            return cl;
        }
    }

    /** Resolve every <code>.jar</code> from every loader into a fast lookup map. */
    private void initJars() {
        log.debug("initJars() - scanning loaders for .jar files");
        orderedLoaders.forEach(loader -> {
            for (URL url : loader.getURLs()) {
                File f = new File(url.getFile());
                if (!f.getName().endsWith(".jar")) continue;
                try {
                    JarFile jar = new JarFile(f);
                    orderedJars.put(loader, jar);
                    log.debug("{}  (loader '{}')", jar.getName(), loader.getName());
                } catch (IOException e) {
                    log.error("Error opening {}", f, e);
                }
            }
        });
    }

    /* ========================================================================== */
    /*  Core class‑loading overrides                                             */
    /* ========================================================================== */

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        // ① fast-path: positive or negative cache
        Class<?> c = resolved.get(name);
        if (c != null) return c;
        if (notFound.contains(name)) throw new ClassNotFoundException(name);

        // ② child-first probing
        for (URLClassLoader l : orderedLoaders) {
            try {
                c = Class.forName(name, false, l);
                resolved.put(name, c);
                log.debug("findClass({}) ⇒ {} (loader {})", name, c.getName(), l.getName());
                return c;
            } catch (ClassNotFoundException ignored) {}
        }

        // ③ remember the miss so we don’t loop again next time
        notFound.add(name);
        throw new ClassNotFoundException(name);
    }


    /* ========================================================================== */
    /*  Component‑based lookup helpers (re‑implemented with interface contract)  */
    /* ========================================================================== */

    /**
     * Locate the <strong>highest‑priority</strong> concrete implementation of the
     * component referenced by <code>name</code>.
     * <p>
     *   <ul>
     *     <li>First we identify the interface annotated with
     *         <pre>@Init(module = ModuleNames.INTERFACE, component = name)</pre>.</li>
     *     <li>We then scan all concrete classes that implement this interface
     *         and pick the one whose module annotation carries the greatest
     *         <code>modulePriority</code>.</li>
     *   </ul>
     * </p>
     *
     * @throws ClassNotFoundException if the interface or an implementation is
     *                                missing.
     */
    protected Class<?> findClass(ComponentNames name) throws ClassNotFoundException {
        ensureComponentsLoaded();

        /* Step 1: identify the interface. */
        Class<?> iface = null;
        for (Class<?> c : buildComponents) {
            if (!c.isInterface()) continue;
            Init ann = c.getAnnotation(Init.class);
            if (ann != null && ann.module() == ModuleNames.INTERFACE && ann.component() == name) {
                iface = c;
                break; // assume a single interface per component
            }
        }
        if (iface == null) {
            throw new ClassNotFoundException("No interface annotated for component " + name);
        }

        return getTopLevelComponent(iface);
    }

    private static Class<?> getTopLevelComponent(Class<?> iface) throws ClassNotFoundException {
        Class<?> best = null;
        int bestPrio = Integer.MIN_VALUE;

        for (Class<?> c : buildComponents) {
            if (c.isInterface()) continue;
            if (!iface.isAssignableFrom(c)) continue;

            Init ann = c.getAnnotation(Init.class);
            if (ann == null) continue; // defensive – every impl should have it

            int prio = ann.module().modulePriority;
            if (prio > bestPrio) {
                best = c;
                bestPrio = prio;
            }
        }
        if (best == null) {
            throw new ClassNotFoundException("No implementation of " + iface.getName());
        }
        return best;
    }

    /**
     * Return <em>all</em> concrete implementations for the given component,
     * ordered from highest to lowest priority.
     */
    protected List<Class<?>> findClasses(ComponentNames name) throws ClassNotFoundException {
        ensureComponentsLoaded();

        /* Identify the interface. */
        Class<?> iface = null;
        for (Class<?> c : buildComponents) {
            if (!c.isInterface()) continue;
            Init ann = c.getAnnotation(Init.class);
            if (ann != null && ann.module() == ModuleNames.INTERFACE && ann.component() == name) {
                iface = c;
                break;
            }
        }
        if (iface == null) {
            throw new ClassNotFoundException("No interface annotated for component " + name);
        }

        /* Collect implementations. */
        List<Class<?>> list = new ArrayList<>();
        for (Class<?> c : buildComponents) {
            if (c.isInterface()) continue;
            if (!iface.isAssignableFrom(c)) continue;
            list.add(c);
        }
        if (list.isEmpty()) {
            throw new ClassNotFoundException("No implementation of " + iface.getName());
        }

        list.sort(Comparator.<Class<?>>comparingInt(
                cl -> cl.getAnnotation(Init.class).module().modulePriority)
            .reversed());
        return list;
    }

    /* ──────────────────────────────────────────────────────────────────────
     *  Locate the highest-priority concrete class for “component + subComp”
     *  (only valid when the interface’s @Init has allowMultiple = true)
     * ─────────────────────────────────────────────────────────────────── */
    protected Class<?> findSubComponent(ComponentNames comp,
                                        SubComponentNames sub) throws ClassNotFoundException {
        if (sub == SubComponentNames.NONE)
            throw new IllegalArgumentException("Sub-component may not be NONE here");

        ensureComponentsLoaded();

        Class<?> iface = locateInterface(comp);
        Class<?> best = null;
        int bestPrio = Integer.MIN_VALUE;
        for (Class<?> c : buildComponents) {
            if (c.isInterface() || !iface.isAssignableFrom(c)) continue;
            Init an = c.getAnnotation(Init.class);
            if (an == null || an.subComp() != sub) continue;

            int prio = an.module().modulePriority;
            if (prio > bestPrio) {
                best = c;
                bestPrio = prio;
            }
        }
        if (best == null)
            throw new ClassNotFoundException("No implementation of " + comp
                + " / " + sub);

        log.debug("SubComponent [{} / {}] resolved to {} (prio {})",
            comp, sub, best.getSimpleName(), bestPrio);
        return best;
    }

    private static Class<?> locateInterface(ComponentNames comp) throws ClassNotFoundException {
        Class<?> iface = null;
        boolean multiAllowed = false;
        for (Class<?> c : buildComponents) {
            Init an = c.getAnnotation(Init.class);
            if (c.isInterface() && an != null
                && an.module() == ModuleNames.INTERFACE
                && an.component() == comp) {
                iface = c;
                multiAllowed = an.allowMultiple();
                break;
            }
        }
        if (iface == null)
            throw new ClassNotFoundException("No interface annotated for " + comp);
        if (!multiAllowed)
            throw new IllegalStateException("Interface " + iface.getSimpleName()
                + " does not allow multiple sub-components");
        return iface;
    }

    /* ──────────────────────────────────────────────────────────────────────
     *  Return every concrete class for the given component’s sub-set,
     *  ordered high → low priority (only for allowMultiple = true).
     * ─────────────────────────────────────────────────────────────────── */
    protected List<Class<?>> findSubComponents(ComponentNames comp) throws ClassNotFoundException {
        ensureComponentsLoaded();
        Class<?> iface = locateInterface(comp);

        List<Class<?>> list = new ArrayList<>();
        for (Class<?> c : buildComponents) {
            if (c.isInterface() || !iface.isAssignableFrom(c)) continue;
            list.add(c);
        }
        if (list.isEmpty())
            throw new ClassNotFoundException("No sub-components for " + comp);

        list.sort(Comparator.<Class<?>>comparingInt(
            cl -> cl.getAnnotation(Init.class)
                .module().modulePriority).reversed());

        log.debug("SubComponent list for {} → {}", comp, list);
        return list;
    }

    /* ========================================================================== */
    /*  Factory helpers – public API                                             */
    /* ========================================================================== */

    @SuppressWarnings("unchecked")
    public <T extends IBaseComp> T tryCreate(ComponentNames name, Object... params) {
        try {
            return (T) createInstance(findClass(name), params);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T tryCreate(ComponentNames name, PlatformNames platform, Object... params) {
        try {
            for (Class<?> c : findClasses(name)) {
                Init ann = c.getAnnotation(Init.class);
                if (ann.platform() == platform) {
                    return (T) createInstance(c, params);
                }
            }
        } catch (ClassNotFoundException ignored) {
        }
        throw new IllegalStateException("Cannot create component " + name + " for platform " + platform);
    }

    /* Single sub-component creation + ID registration */
    @SuppressWarnings("unchecked")
    public <T extends IBaseComp> T tryCreate(ComponentNames comp, SubComponentNames sub, Object... params) {
        try {
            Class<?> cls = findSubComponent(comp, sub);
            T inst = (T) createInstance(cls, params);

            Integer id = inst.getMultId();
            log.debug("Created multi-component {} id={}", cls.getSimpleName(), id);
            return inst;
        } catch (ClassNotFoundException e) {
            return null;   // consistent with original tryCreate()
        }
    }

    /* Bulk creation of every sub-component */
    @SuppressWarnings("unchecked")
    public <T extends IBaseComp> List<T> tryCreateAll(ComponentNames comp, Object... actorParams) {
        try {
            List<Class<?>> classes = findSubComponents(comp);
            List<T> out = new ArrayList<>(classes.size());
            for (Class<?> cls : classes) {
                T inst = (T) createInstance(cls, actorParams);
                Integer id = inst.getMultId();
                log.debug("Created multi-component {} id={}", cls.getSimpleName(), id);
                out.add(inst);
            }
            return out;
        } catch (ClassNotFoundException e) {
            return Collections.emptyList();
        }
    }

    /* ========================================================================== */
    /*  Annotation‑scan utilities                                               */
    /* ========================================================================== */

    /**
     * Scan every JAR entry of every module for classes bearing the specified
     * annotation. The operation is cached in {@link #buildComponents} because
     * it can be expensive on large games.
     */
    public List<Class<?>> getAnnotated(Class<? extends Annotation> anno) {
        log.debug("getAnnotated({})", anno.getSimpleName());
        List<Class<?>> out = new ArrayList<>();
        orderedLoaders.forEach(loader -> {
            JarFile jar = orderedJars.get(loader);
            if (jar == null)
                throw new IllegalStateException("Null jar: " + loader.getName());
            Enumeration<JarEntry> e = jar.entries();
            while (e.hasMoreElements()) {
                JarEntry entry = e.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                String cls = entry.getName().replace('/', '.').replace(".class", "");
                try {
                    Class<?> c = loader.loadClass(cls);
                    if (c.getAnnotation(anno) != null) {
                        out.add(c);
                    }
                } catch (Throwable ignored) {
                }
            }
        });
        log.debug("found {} annotated classes", out.size());
        return out;
    }

    /* ========================================================================== */
    /*  Reflection convenience                                                   */
    /* ========================================================================== */
    public Object createInstance(Class<?> clazz, Object... params) {
        log.debug("createInstance({})", clazz.getSimpleName());
        try {
            Constructor<?> ctor;

            if (params.length == 0) {
                ctor = clazz.getDeclaredConstructor();
            } else {
                try {
                    Class<?>[] types = Arrays.stream(params)
                        .map(Object::getClass)
                        .toArray(Class<?>[]::new);
                    ctor = clazz.getDeclaredConstructor(types);
                } catch (NoSuchMethodException e) {
                    ctor = clazz.getDeclaredConstructor();
                    params = new Object[0];
                }
            }

            ctor.setAccessible(true);
            return ctor.newInstance(params);

        } catch (Exception e) {
            throw new IllegalStateException("Failed to create instance of " + clazz.getName(), e);
        }
    }

    /* ========================================================================== */
    /*  Helper DTO                                                              */
    /* ========================================================================== */
    private static class ProjectLoader {
        final URLClassLoader classLoader;
        final int level;
        ProjectLoader(URLClassLoader cl, int lvl) {
            classLoader = cl;
            level = lvl;
        }
    }

    /* ==================================================================
     *  Duplicate-elimination for multi-sub-components
     * =================================================================*/
    private void filterBuildComponents() {
        if (buildComponents.isEmpty()) return;          // nothing to do

        Map<String, Class<?>>  bestForKey = new HashMap<>();
        Set<Class<?>>          interfaces = new HashSet<>();
        List<Class<?>>         singles    = new ArrayList<>();

        for (Class<?> c : buildComponents) {
            Init an = c.getAnnotation(Init.class);
            if (an == null) continue;                  // should not happen

            if (c.isInterface()) {
                interfaces.add(c);
                continue;
            }

            if (an.subComp() == SubComponentNames.NONE) {
                // single-component: keep behaviour unchanged
                singles.add(c);
                continue;
            }

            /* Key = Component + SubComponent pair ----------------------- */
            String key = an.component().name() + "#" + an.subComp().name();
            Class<?> incumbent = bestForKey.get(key);

            if (incumbent == null) {
                bestForKey.put(key, c);
            } else {
                int oldPrio = incumbent.getAnnotation(Init.class)
                    .module().modulePriority;
                int newPrio = an.module().modulePriority;
                if (newPrio > oldPrio) {
                    bestForKey.put(key, c);
                }
            }
        }

        /* Replace buildComponents with the filtered view ---------------- */
        buildComponents.clear();
        buildComponents.addAll(interfaces);
        buildComponents.addAll(singles);
        buildComponents.addAll(bestForKey.values());

        // --- logging ----------------------------------------------------
        bestForKey.forEach((k, cls) -> {
            Init an = cls.getAnnotation(Init.class);
            log.log("Multi-component [{}] resolved to {}  (prio {})",
                k, cls.getSimpleName(), an.module().modulePriority);
        });
    }

    /* Ensure we load & filter exactly once --------------------------------*/
    private synchronized void ensureComponentsLoaded() {
        if (buildComponents.isEmpty()) {
            buildComponents.addAll(getAnnotated(Init.class));
            filterBuildComponents();
        }
    }
}
