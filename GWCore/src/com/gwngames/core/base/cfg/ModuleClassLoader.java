package com.gwngames.core.base.cfg;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.ex.ErrorPopupException;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.*;

import java.io.File;
import java.io.FileReader;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;

/**
 * Central class-loader for the GW Framework runtime.
 *
 * <p>The loader is created once per process and is responsible for</p>
 * <ol>
 *   <li>reading <kbd>bin/config.json</kbd> produced by the Gradle build,</li>
 *   <li>creating a dedicated {@link URLClassLoader} for each module JAR,</li>
 *   <li>exposing type-lookup helpers that respect the framework’s
 *       component / sub-component / priority rules.</li>
 * </ol>
 *
 * <p>Both the legacy <em>single-implementation</em> path and the new
 * <em>multi-sub-component</em> path are supported; the caller decides which
 * helper to use.</p>
 */
public final class ModuleClassLoader extends ClassLoader {

    /* -------------------------------------------------------------------- *
     * constants & singletons                                               *
     * -------------------------------------------------------------------- */
    private static final FileLogger LOG = FileLogger.get(LogFiles.SYSTEM);
    private static final List<Class<?>> COMPONENTS = new ArrayList<>();
    private static ModuleClassLoader INSTANCE;

    /* -------------------------------------------------------------------- *
     * per-instance state                                                   *
     * -------------------------------------------------------------------- */
    private final List<URLClassLoader> loaders = new ArrayList<>();
    private final Map<URLClassLoader, JarFile> jars = new LinkedHashMap<>();

    /* positive / negative caches for single-class look-up */
    private final Map<String, Class<?>> resolved = new ConcurrentHashMap<>();
    private final Set<String> notFound = ConcurrentHashMap.newKeySet();

    /* caches for class lists (per component) */
    private final Map<ComponentNames, List<Class<?>>> classLists = new ConcurrentHashMap<>();
    private final Set<ComponentNames> listMisses = ConcurrentHashMap.newKeySet();

    /* -------------------------------------------------------------------- *
     * construction                                                         *
     * -------------------------------------------------------------------- */
    private ModuleClassLoader() throws ErrorPopupException {
        super(ModuleClassLoader.class.getClassLoader());
        initLoaders();
        initJars();
    }

    /** Thread-safe singleton accessor. */
    public static synchronized ModuleClassLoader getInstance() {
        if (INSTANCE == null) {
            try {
                INSTANCE = new ModuleClassLoader();
            } catch (ErrorPopupException e) {
                throw new RuntimeException(e);
            }
        }
        return INSTANCE;
    }

    /* -------------------------------------------------------------------- *
     * loader & JAR discovery                                               *
     * -------------------------------------------------------------------- */
    private void initLoaders() throws ErrorPopupException {
        String forcedBin = System.getProperty("gw.bin.dir");
        if (forcedBin != null) {
            buildLoadersFromBin(new File(forcedBin));
            return;
        }

        File exec = locateExecutableDir();
        File bin  = locateBinDir(exec);
        buildLoadersFromBin(bin);
    }

    private File locateExecutableDir() throws ErrorPopupException {
        try {
            URL src = getClass().getProtectionDomain().getCodeSource().getLocation();
            File where = new File(src.toURI());
            return where.isFile() ? where.getParentFile() : where;
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            throw new ErrorPopupException("Cannot determine executable location");
        }
    }

    private File locateBinDir(File exec) throws ErrorPopupException {
        if (new File(exec, "config.json").isFile()) return exec;
        if ("lib".equals(exec.getName())) {
            File bin = new File(exec.getParentFile(), "bin");
            if (new File(bin, "config.json").isFile()) return bin;
        }
        throw new ErrorPopupException("Unable to locate <bin> directory near " + exec);
    }

    @SuppressWarnings("unchecked")
    private void buildLoadersFromBin(File bin) throws ErrorPopupException {
        LOG.debug("Initialising loaders from {}", bin);

        /* parse config --------------------------------------------------- */
        Map<String,Object> cfg;
        try (JsonReader r = new JsonReader(new FileReader(new File(bin, "config.json")))) {
            cfg = new Gson().fromJson(r, Map.class);
        } catch (Exception e) {
            LOG.error(e.getMessage(), e);
            throw new ErrorPopupException("Cannot read config.json");
        }

        String gameType = (String) cfg.get("gameType");
        List<Map<String,Object>> projects = (List<Map<String,Object>>) cfg.get("projects");
        if (projects == null || projects.isEmpty())
            throw new ErrorPopupException("config.json lists no projects");

        File root = bin.getParentFile();            // parent of bin & lib
        List<ProjectLoader> tmp = new ArrayList<>();

        for (Map<String,Object> p : projects) {
            String jarRel = (String) p.get("jar");
            Number lvl    = (Number) p.get("level");
            String type   = (String) p.get("type");
            if (!gameType.equals(type) || jarRel == null) continue;

            File jar = new File(root, jarRel);
            if (!jar.exists()) {
                LOG.error("JAR not found: {}", jar);
                continue;
            }
            URLClassLoader cl = createNamedLoader(jar);
            tmp.add(new ProjectLoader(cl, lvl.intValue()));
            LOG.debug("Loader '{}' level {}", cl.getName(), lvl);
        }

        tmp.sort(Comparator.comparingInt(pl -> -pl.level));
        tmp.forEach(pl -> loaders.add(pl.loader));
    }

    private URLClassLoader createNamedLoader(File jar) throws ErrorPopupException {
        try {
            return URLClassLoader.class
                .getConstructor(String.class, URL[].class, ClassLoader.class)
                .newInstance(jar.getName(), new URL[]{jar.toURI().toURL()}, this);
        } catch (ReflectiveOperationException | MalformedURLException e) {
            LOG.error(e.getMessage(), e);
            throw new ErrorPopupException("Cannot create class loader for " + jar.getName());
        }
    }

    private void initJars() {
        loaders.forEach(l -> Arrays.stream(l.getURLs())
            .filter(u -> u.getFile().endsWith(".jar"))
            .forEach(u -> {
                try {
                    jars.put(l, new JarFile(new File(u.getFile())));
                } catch (Exception ex) {
                    LOG.error("Could not open {}", u, ex);
                }
            }));
    }

    /* -------------------------------------------------------------------- *
     * core ClassLoader override                                            *
     * -------------------------------------------------------------------- */
    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> hit = resolved.get(name);
        if (hit != null) return hit;
        if (notFound.contains(name)) throw new ClassNotFoundException(name);

        for (URLClassLoader l : loaders) {
            try {
                Class<?> c = Class.forName(name, false, l);
                resolved.put(name, c);
                return c;
            } catch (ClassNotFoundException ignored) { }
        }
        notFound.add(name);
        throw new ClassNotFoundException(name);
    }

    /* -------------------------------------------------------------------- *
     * public factory helpers                                               *
     * -------------------------------------------------------------------- */

    public <T extends IBaseComp> T tryCreate(ComponentNames comp, Object... args) {
        try { return createInstance(findClass(comp), args); }
        catch (ClassNotFoundException e) { return null; }
    }

    public <T extends IBaseComp> T tryCreate(ComponentNames comp, SubComponentNames sub,
                                             Object... args) {
        try { return createInstance(findSubComponent(comp, sub), args); }
        catch (ClassNotFoundException e) { return null; }
    }

    public <T extends IBaseComp> List<T> tryCreateAll(ComponentNames comp,
                                                      Object... args) {
        try {
            List<Class<?>> impls = findSubComponents(comp);
            List<T> list = new ArrayList<>(impls.size());
            for (Class<?> c : impls) list.add(createInstance(c, args));
            return list;
        } catch (ClassNotFoundException e) {
            return Collections.emptyList();
        }
    }

    /* -------------------------------------------------------------------- *
     * component / sub-component resolution                                 *
     * -------------------------------------------------------------------- */

    private Class<?> findClass(ComponentNames comp) throws ClassNotFoundException {
        ensureComponentsLoaded();
        return pickHighestPrio(new HashMap<>(), comp.name(), locateInterface(comp));
    }

    public List<Class<?>> findClasses(ComponentNames comp) throws ClassNotFoundException {
        List<Class<?>> cached = classLists.get(comp);
        if (cached != null) return cached;
        if (listMisses.contains(comp))
            throw new ClassNotFoundException("No interface for " + comp);

        ensureComponentsLoaded();
        List<Class<?>> list = collectConcrete(locateInterface(comp));
        classLists.put(comp, list);
        return list;
    }

    private Class<?> findSubComponent(ComponentNames comp, SubComponentNames sub)
        throws ClassNotFoundException {
        ensureComponentsLoaded();
        Class<?> iface = locateInterface(comp, true);
        Map<String,Class<?>> bucket = new HashMap<>();
        String key = comp.name() + "#" + sub.name();
        for (Class<?> c : collectConcrete(iface)) {
            if (c.getAnnotation(Init.class).subComp() == sub)
                pickHighestPrio(bucket, key, c);
        }
        Class<?> best = bucket.get(key);
        if (best == null)
            throw new ClassNotFoundException("No implementation of " + key);
        return best;
    }

    private List<Class<?>> findSubComponents(ComponentNames comp) throws ClassNotFoundException {
        ensureComponentsLoaded();
        Class<?> iface = locateInterface(comp, true);
        List<Class<?>> list = collectConcrete(iface);
        list.sort(PRIO_DESC);
        return list;
    }

    /* -------------------------------------------------------------------- *
     * helpers                                                              *
     * -------------------------------------------------------------------- */

    private static final Comparator<Class<?>> PRIO_DESC =
        Comparator.<Class<?>>comparingInt(c -> c.getAnnotation(Init.class)
                .module().modulePriority)
            .reversed();

    private Class<?> locateInterface(ComponentNames comp) throws ClassNotFoundException {
        return locateInterface(comp, false);
    }

    private Class<?> locateInterface(ComponentNames comp, boolean requireMulti)
        throws ClassNotFoundException {

        for (Class<?> c : COMPONENTS) {
            Init a = c.getAnnotation(Init.class);
            if (c.isInterface() && a != null
                && a.module() == ModuleNames.INTERFACE
                && a.component() == comp) {
                if (requireMulti && !a.allowMultiple())
                    throw new IllegalStateException(
                        "Interface " + c.getSimpleName() + " does not allow multiple sub-components");
                return c;
            }
        }
        throw new ClassNotFoundException("No interface annotated for " + comp);
    }

    private List<Class<?>> collectConcrete(Class<?> iface) {
        List<Class<?>> list = new ArrayList<>();
        for (Class<?> c : COMPONENTS)
            if (!c.isInterface() && iface.isAssignableFrom(c))
                list.add(c);
        return list;
    }

    private Class<?> pickHighestPrio(Map<String,Class<?>> map,
                                     String key, Class<?> candidate) {
        Class<?> incumbent = map.get(key);
        if (incumbent == null) {
            map.put(key, candidate);
            return candidate;
        }
        int oldP = incumbent.getAnnotation(Init.class).module().modulePriority;
        int newP = candidate.getAnnotation(Init.class).module().modulePriority;
        if (newP > oldP) map.put(key, candidate);
        return map.get(key);
    }

    private <T> T createInstance(Class<?> cls, Object... args) {
        try {
            Constructor<?> ctor = args.length == 0
                ? cls.getDeclaredConstructor()
                : cls.getDeclaredConstructor(Arrays.stream(args)
                .map(Object::getClass)
                .toArray(Class<?>[]::new));
            ctor.setAccessible(true);
            @SuppressWarnings("unchecked") T obj = (T) ctor.newInstance(args);
            if (obj instanceof IBaseComp comp) comp.getMultId();   // register ID if needed
            return obj;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot instantiate " + cls.getName(), e);
        }
    }

    /* -------------------------------------------------------------------- *
     * annotation scan & de-duplication                                     *
     * -------------------------------------------------------------------- */

    public List<Class<?>> getAnnotated(Class<? extends Annotation> anno) {
        List<Class<?>> out = new ArrayList<>();
        jars.forEach((ldr, jar) -> jar.stream()
            .filter(e -> e.getName().endsWith(".class"))
            .forEach(e -> {
                String name = e.getName().replace('/', '.')
                    .replace(".class", "");
                try {
                    Class<?> c = Class.forName(name, false, ldr);
                    if (c.getAnnotation(anno) != null) out.add(c);
                } catch (Throwable ignored) { }
            }));
        return out;
    }

    private void ensureComponentsLoaded() {
        if (!COMPONENTS.isEmpty()) return;
        COMPONENTS.addAll(getAnnotated(Init.class));
        filterDuplicates();
    }

    /** keep only highest-priority implementation per (component, subComp). */
    private void filterDuplicates() {
        Map<String, Class<?>> best = new HashMap<>();
        Iterator<Class<?>> it = COMPONENTS.iterator();

        while (it.hasNext()) {
            Class<?> c = it.next();
            Init a = c.getAnnotation(Init.class);
            if (c.isInterface() || a.subComp() == SubComponentNames.NONE) continue;

            String key = a.component().name() + "#" + a.subComp().name();
            Class<?> incumbent = best.get(key);
            if (incumbent == null
                || a.module().modulePriority >
                incumbent.getAnnotation(Init.class).module().modulePriority) {
                best.put(key, c);
            }
            it.remove();                         // remove for now
        }
        COMPONENTS.addAll(best.values());        // re-insert winners
        LOG.debug("After de-duplication we have {} component classes", COMPONENTS.size());
    }

    /* -------------------------------------------------------------------- *
     * simple DTO                                                            *
     * -------------------------------------------------------------------- */
    private record ProjectLoader(URLClassLoader loader, int level) { }
}
