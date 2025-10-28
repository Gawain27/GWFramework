package com.gwngames.core.base.cfg;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.base.cfg.IClassLoader;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.ex.ErrorPopupException;
import com.gwngames.core.base.cfg.i18n.CoreTranslation;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.*;
import com.gwngames.core.util.ComponentUtils;
import com.gwngames.core.util.TransformingURLClassLoader;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;

/**
 * Dynamic class-loader for GW Framework modules.
 * <p>
 * If a concrete class annotated with {@link Init}
 * specifies <em>subComp ≠ NONE</em> but leaves <code>component()</code> and/or
 * <code>module()</code> as the sentinel value <strong>AUTO</strong>,
 * those missing attributes are automatically inherited from the first
 * superclass that declares them.  This is resolved at runtime – no byte-code
 * changes are made.
 * </p>
 */
@Init(module = ModuleNames.CORE)
public final class ModuleClassLoader extends ClassLoader implements IClassLoader {

    /* ───────────────────────── static members ───────────────────────── */
    private static final FileLogger log = FileLogger.get(LogFiles.SYSTEM);

    /** Interfaces annotated with <code>@Init(module = INTERFACE)</code>. */
    private static final List<Class<?>> interfaceTypes = new ArrayList<>();
    /** Concrete classes kept after duplicate-pruning. */
    private static final List<Class<?>> concreteTypes  = new ArrayList<>();
    /** Keyed by "COMPONENT#SUB" (SUB can be NONE). Each list is sorted by modulePriority DESC.*/
    private final Map<String, List<Class<?>>> allConcreteByKey = new ConcurrentHashMap<>();

    private final Map<Class<?>, Object> singletons = new ConcurrentHashMap<>();

    private static ModuleClassLoader INSTANCE;

    /* positive / negative lookup caches */
    private final Map<String, Class<?>> resolved = new ConcurrentHashMap<>();
    private final Set<String>           misses   = ConcurrentHashMap.newKeySet();

    /* per-instance state */
    private final List<URLClassLoader> loaders      = new ArrayList<>();
    private final Map<URLClassLoader, JarFile> jars = new LinkedHashMap<>();
    private final List<URLClassLoader> dirLoaders = new ArrayList<>();
    private final List<ProjectLoader> classLoaders = new ArrayList<>();

    /* ==================================================================== */
    /*  Singleton                                                           */
    /* ==================================================================== */
    public static synchronized ModuleClassLoader getInstance() {
        if (INSTANCE == null) {
            try {
                log.info("Initializing ModuleClassLoader singleton...");
                INSTANCE = new ModuleClassLoader();
                INSTANCE.ensureTypesLoaded();
                log.info("ModuleClassLoader initialized successfully.");
            } catch (ErrorPopupException e) {
                log.error("Failed to initialize ModuleClassLoader", e);
                throw new RuntimeException(e);
            }
        }
        return INSTANCE;
    }

    private ModuleClassLoader() throws ErrorPopupException {
        super(ModuleClassLoader.class.getClassLoader());
        log.debug("ModuleClassLoader created with parent ClassLoader: {}", getParent());
        initLoaders();
        initJars();
        //addAppClasspathRootsToScan();
        log.info("ModuleClassLoader setup completed with {} class loaders and {} JARs.",
            loaders.size(), jars.size());
    }

    /* ==================================================================== */
    /*  Loader / JAR discovery                                              */
    /* ==================================================================== */

    private void initLoaders() throws ErrorPopupException {
        String forcedBin = System.getProperty("gw.bin.dir");
        if (forcedBin != null) {
            log.info("Using forced binary directory: {}", forcedBin);
            useBinDir(new File(forcedBin));
            return;
        }

        File whereAmI;
        try {
            URL src = getClass().getProtectionDomain().getCodeSource().getLocation();
            whereAmI = new File(src.toURI());
            log.debug("ModuleClassLoader source location: {}", src);
            if (whereAmI.isFile()) whereAmI = whereAmI.getParentFile();
        } catch (Exception e) {
            log.error("Executable location not found.", e);
            throw new ErrorPopupException(CoreTranslation.EXE_NOT_FOUND);
        }

        File binDir;
        if (new File(whereAmI, "config.json").isFile()) {
            binDir = whereAmI;
        } else if ("lib".equals(whereAmI.getName())) {
            binDir = new File(whereAmI.getParentFile(), "bin");
        } else {
            log.error("Binary directory not found near: {}", whereAmI);
            throw new ErrorPopupException(CoreTranslation.BIN_NOT_FOUND, whereAmI.toString());
        }

        log.info("Using binary directory: {}", binDir);
        useBinDir(binDir);
    }

    @SuppressWarnings("unchecked")
    private void useBinDir(File binDir) throws ErrorPopupException {
        Map<String, Object> cfg;
        File cfgFile = new File(binDir, "config.json");
        try (JsonReader r = new JsonReader(new FileReader(cfgFile))) {
            log.debug("Reading configuration from {}", cfgFile);
            cfg = new Gson().fromJson(r, new TypeToken<Map<String,Object>>(){}.getType());
        } catch (Exception e) {
            log.error("Configuration file not found: {}", cfgFile, e);
            throw new ErrorPopupException(CoreTranslation.CONFIG_NOT_FOUND);
        }

        String gameType = (String) cfg.get("gameType");
        List<Map<String,Object>> projects = (List<Map<String,Object>>) cfg.get("projects");
        if (projects == null || projects.isEmpty()) {
            log.error("No projects found in configuration.");
            throw new ErrorPopupException(CoreTranslation.PROJECTS_NOT_FOUND);
        }

        File root = binDir.getParentFile();
        int loaderCount = 0;
        for (Map<String,Object> p : projects) {
            if (!gameType.equals(p.get("type"))) continue;
            File jar = new File(root, (String) p.get("jar"));
            if (!jar.exists()) continue;
            try {
                URLClassLoader cl = new TransformingURLClassLoader(jar.getName(), new URL[]{jar.toURI().toURL()}, this, this);
                loaders.add(cl);
                classLoaders.add(new ProjectLoader(cl, (int) Double.parseDouble(String.valueOf(p.get("level")))));
                loaderCount++;
            } catch (MalformedURLException e) {
                log.error("JAR not found: {}", jar, e);
                throw new ErrorPopupException(CoreTranslation.JAR_NOT_FOUND, jar.toString());
            }
        }
        loaders.sort(Comparator.comparing(ClassLoader::getName));
        log.info("{} project class loaders initialized.", loaderCount);
    }

    /**
     * @see TransformingURLClassLoader
     * */
    @Deprecated()
    private URLClassLoader createNamedLoader(File jar) throws MalformedURLException {
        URL url = jar.toURI().toURL();
        try {   // Java 9+
            return URLClassLoader.class
                .getConstructor(String.class, URL[].class, ClassLoader.class)
                .newInstance(jar.getName(), new URL[]{url}, this);
        } catch (ReflectiveOperationException ignored) {
            return new URLClassLoader(new URL[]{url}, this);
        }
    }

    /** Add application classpath roots (dirs and jars) to our scan sets, so tests/dev work. */
    @Deprecated
    private void addAppClasspathRootsToScan() {
        try {
            String cp = System.getProperty("java.class.path");
            if (cp == null || cp.isBlank()) {
                log.debug("No java.class.path detected; skipping app classpath scan.");
                return;
            }
            String[] entries = cp.split(File.pathSeparator);
            int addedDirs = 0, addedJars = 0;

            for (String e : entries) {
                if (e == null || e.isBlank()) continue;
                File f = new File(e);
                if (!f.exists()) continue;

                URL url = f.toURI().toURL();
                // Use a tiny throwaway URLClassLoader just for scanning.
                URLClassLoader cl = new URLClassLoader("appcp:" + f.getName(), new URL[]{url}, getParent());

                if (f.isDirectory()) {
                    dirLoaders.add(cl);
                    addedDirs++;
                } else if (f.getName().endsWith(".jar")) {
                    try {
                        jars.put(cl, new JarFile(f));
                        addedJars++;
                    } catch (IOException ioe) {
                        log.debug("Skipping unreadable JAR on classpath: {}", f, ioe);
                    }
                }
            }
            log.info("App classpath scan enabled: +{} dirs, +{} jars", addedDirs, addedJars);
        } catch (Throwable t) {
            log.debug("Failed to attach app classpath to scan.", t);
        }
    }

    private void initJars() {
        int jarCount = 0, dirCount = 0;
        for (URLClassLoader l : loaders) {
            for (URL u : l.getURLs()) {
                File f = new File(u.getFile());
                if (f.isDirectory()) {
                    dirLoaders.add(l);
                    dirCount++;
                } else if (f.getName().endsWith(".jar")) {
                    try {
                        jars.put(l, new JarFile(f));
                        jarCount++;
                    } catch (IOException e) {
                        log.error("Error opening JAR file {}", f, e);
                    }
                }
            }
        }
        log.info("Initialized {} JAR files and {} class directories.", jarCount, dirCount);
    }

    /* ==================================================================== */
    /*  Class-loading override – child-first with caches                    */
    /* ==================================================================== */

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> hit = resolved.get(name);
        if (hit != null) return hit;
        if (misses.contains(name)) throw new ClassNotFoundException(name);

        final String path = name.replace('.', '/') + ".class";

        for (URLClassLoader l : loaders) {
            try {
                // key guard: only try this child if it physically has the class
                if (l.findResource(path) == null) continue;

                Class<?> c = Class.forName(name, false, l); // lets the child weave if needed
                resolved.put(name, c);
                log.debug("Class {} loaded by {}", name, l.getName());
                return c;
            } catch (ClassNotFoundException ignore) {
                // this child advertised the resource but failed to define it; try next
            }
        }

        misses.add(name);
        log.error("Class {} not found.", name);
        throw new ClassNotFoundException(name);
    }

    /* ==================================================================== */
    /*  Annotation scanning & de-duplication                                */
    /* ==================================================================== */
    private synchronized void ensureTypesLoaded() {
        if (!interfaceTypes.isEmpty() || !concreteTypes.isEmpty()) return;
        log.info("Scanning for @Init-annotated types …");

        List<Class<?>> found = scanForAnnotated(Init.class);
        log.info("Found {} @Init-annotated classes.", found.size());

        Map<String, Class<?>> bestMulti = new HashMap<>();
        // NEW: collect all candidates per (component, subComp)
        Map<String, List<Class<?>>> allByKeyTmp = new HashMap<>();

        for (Class<?> c : found) {
            Init an = IClassLoader.resolvedInit(c);

            if (c.isInterface()) {
                if (IBaseComp.class.isAssignableFrom(c)) {
                    interfaceTypes.add(c);
                } else {
                    log.debug("Skipping interface {} – does not extend IBaseComp", c.getName());
                }
                continue;
            }

            // Only consider concrete classes that implement some IBaseComp interface
            boolean implementsIBaseComp = IBaseComp.class.isAssignableFrom(c);
            if (!implementsIBaseComp) continue;

            // Index ALL concrete classes by (component, subComp) – including NONE
            String groupKey = IClassLoader.keyOf(an.component(), an.subComp());
            allByKeyTmp.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(c);

            // Existing behavior: if subComp == NONE, we keep it; for multi we record best
            if (an.subComp() == SubComponentNames.NONE) {
                concreteTypes.add(c);
                continue;
            }

            // choose highest-priority impl per (component, subComp)
            String k = an.component().name() + "#" + an.subComp().name();
            Class<?> incumbent = bestMulti.get(k);
            if (incumbent == null ||
                an.module().modulePriority >
                    IClassLoader.resolvedInit(incumbent).module().modulePriority) {
                bestMulti.put(k, c);
            }
        }

        concreteTypes.addAll(bestMulti.values());
        log.info("{} interface types and {} concrete types registered.", interfaceTypes.size(), concreteTypes.size());

        // Sort & freeze the all-candidates index by modulePriority DESC
        for (Map.Entry<String, List<Class<?>>> e : allByKeyTmp.entrySet()) {
            e.getValue().sort(Comparator.comparingInt(
                (Class<?> cl) -> IClassLoader.resolvedInit(cl).module().modulePriority
            ).reversed());
            allConcreteByKey.put(e.getKey(), List.copyOf(e.getValue()));
        }

        /* existing enum pre-assignment logic follows unchanged … */
        for (Class<?> c : concreteTypes) {
            if (c.isEnum()) {
                instancesOf(c); // walks constants, calls setMultId()
                log.debug("Added enum: {}", c.getSimpleName());
            }
        }
    }

    @Override
    public List<Class<?>> scanForAnnotated(Class<? extends Annotation> ann) {
        List<Class<?>> out = new ArrayList<>();

        // 1) JARs (existing behavior)
        jars.forEach((loader, jar) -> jar.entries().asIterator().forEachRemaining(e -> {
            if (!e.getName().endsWith(".class")) return;
            String cn = e.getName().replace('/', '.').replace(".class", "");
            try {
                Class<?> c = loader.loadClass(cn);
                if (c.getDeclaredAnnotation(Init.class) != null) out.add(c);
                else if (c.getAnnotation(Init.class) != null) out.add(c);
            } catch (Throwable ignored) {}
        }));

        // 2) Directories (new)
        for (URLClassLoader l : dirLoaders) {
            for (URL u : l.getURLs()) {
                File root = new File(u.getFile());
                if (!root.isDirectory()) continue;
                final int rootLen = root.getAbsolutePath().length() + 1;
                Deque<File> stack = new ArrayDeque<>();
                stack.push(root);
                while (!stack.isEmpty()) {
                    File cur = stack.pop();
                    File[] kids = cur.listFiles();
                    if (kids == null) continue;
                    for (File k : kids) {
                        if (k.isDirectory()) {
                            stack.push(k);
                        } else if (k.getName().endsWith(".class")) {
                            String abs = k.getAbsolutePath();
                            String rel = abs.substring(rootLen).replace(File.separatorChar, '.');
                            String cn = rel.substring(0, rel.length() - ".class".length());
                            try {
                                Class<?> c = l.loadClass(cn);
                                if (c.getAnnotation(ann) != null) out.add(c);
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }
        }

        return out;
    }

    /* ==================================================================== */
    /*  Lookup helpers                                                      */
    /* ==================================================================== */

    private Class<?> interfaceOf(ComponentNames comp) throws ClassNotFoundException {
        ensureTypesLoaded();
        for (Class<?> i : interfaceTypes) {
            Init an = i.getAnnotation(Init.class);
            if (an.component() == comp) return i;
        }
        throw new ClassNotFoundException("No interface for component " + comp);
    }

    /* single-component (allowMultiple = false) --------------------------- */
    @Override
    public Class<?> findClass(ComponentNames comp) throws ClassNotFoundException {
        Class<?> iface = interfaceOf(comp);
        Class<?> best  = null; int prio = Integer.MIN_VALUE;
        for (Class<?> c : concreteTypes) {
            if (!iface.isAssignableFrom(c)) continue;
            Init an = IClassLoader.resolvedInit(c);
            if (an.subComp() != SubComponentNames.NONE) continue;
            if (an.module().modulePriority > prio) { best = c; prio = an.module().modulePriority; }
        }
        if (best == null) throw new ClassNotFoundException("No impl of " + comp);
        return best;
    }

    @Override
    public List<Class<?>> findClasses(ComponentNames comp) throws ClassNotFoundException {
        Class<?> iface = interfaceOf(comp);
        List<Class<?>> out = new ArrayList<>();
        for (Class<?> c : concreteTypes) {
            if (!iface.isAssignableFrom(c)) continue;
            Init an = IClassLoader.resolvedInit(c);
            if (an.subComp() != SubComponentNames.NONE) continue;
            out.add(c);
        }
        if (out.isEmpty()) throw new ClassNotFoundException("No impl of " + comp);
        out.sort(Comparator.comparingInt(
            (Class<?> cl) -> Objects.requireNonNull(IClassLoader.resolvedInit(cl)).module().modulePriority).reversed());
        return out;
    }

    /* multi sub-components (allowMultiple = true) ------------------------ */
    @Override
    public Class<?> findSubComponent(ComponentNames comp, SubComponentNames sub)
        throws ClassNotFoundException {

        if (sub == SubComponentNames.NONE) throw new IllegalArgumentException("subComp NONE");
        Class<?> iface = interfaceOf(comp);
        Init ifaceAnn  = iface.getAnnotation(Init.class);
        if (!ifaceAnn.allowMultiple())
            throw new IllegalStateException(iface.getSimpleName()+" does not allow multiple");

        Class<?> best = null; int prio = Integer.MIN_VALUE;
        for (Class<?> c : concreteTypes) {
            Init an = IClassLoader.resolvedInit(c);
            if (an.subComp()!=sub) continue;
            if (!iface.isAssignableFrom(c)) continue;
            if (an.module().modulePriority > prio) { best = c; prio = an.module().modulePriority; }
        }
        if (best == null) throw new ClassNotFoundException("No "+comp+"/"+sub);
        return best;
    }

    private List<Class<?>> findSubComponents(ComponentNames comp) throws ClassNotFoundException {
        Class<?> iface = interfaceOf(comp);
        if (!iface.getAnnotation(Init.class).allowMultiple())
            throw new IllegalStateException(iface.getSimpleName()+" does not allow multiple");

        List<Class<?>> out = new ArrayList<>();
        for (Class<?> c : concreteTypes) {
            Init an = IClassLoader.resolvedInit(c);
            if (iface.isAssignableFrom(c)) {
                if (an.subComp()!=SubComponentNames.NONE) out.add(c);
            }
        }
        if (out.isEmpty()) throw new ClassNotFoundException("No sub-components for "+comp);
        out.sort(Comparator.comparingInt(
            (Class<?> cl) -> Objects.requireNonNull(IClassLoader.resolvedInit(cl)).module().modulePriority).reversed());
        return out;
    }

    /* -------------------------------------------------------------------- */
    /*  SIMPLE COMPONENT (one impl only)                                    */
    /* -------------------------------------------------------------------- */
    @Override
    public <T> T tryCreate(ComponentNames comp, Object... args) {
        try { return firstInstanceOf(findClass(comp), args); }
        catch (ClassNotFoundException e) {
            log.error(e.getMessage());
            return null;
        }
    }

    /* -------------------------------------------------------------------- */
    /*  SIMPLE COMPONENT + SUBCOMPONENT                                     */
    /* -------------------------------------------------------------------- */
    @Override
    public <T> T tryCreate(ComponentNames comp, SubComponentNames sub, Object... args) {
        try {
            T obj = firstInstanceOf(findSubComponent(comp, sub), args);
            if (obj instanceof IBaseComp bc)
                log.debug("Created {} id={}", bc.getClass().getSimpleName(), bc.getMultId());
            return obj;
        } catch (ClassNotFoundException e) { return null; }
    }

    /* -------------------------------------------------------------------- */
    /*  ALL SUBCOMPONENTS (allowMultiple = true)                            */
    /* -------------------------------------------------------------------- */
    @Override
    public <T> List<T> tryCreateAll(ComponentNames comp, Class<?> mustImplement, Object... args) {
        if (mustImplement == null || !mustImplement.isInterface()) {
            throw new IllegalStateException("mustImplement must be a non-null interface");
        }
        try {
            List<Class<?>> clz = findSubComponents(comp);
            List<T> out = new ArrayList<>();
            for (Class<?> c : clz) {
                // accept if the concrete class implements the interface directly or via super-interfaces
                if (mustImplement.isAssignableFrom(c)) {
                    out.addAll(instancesOf(c, args));
                }
            }
            return out;
        } catch (ClassNotFoundException e) {
            return java.util.Collections.emptyList();
        }
    }

    @Override
    public <T> List<T> tryCreateAll(ComponentNames comp, Object... args) {
        try {
            List<Class<?>> clz = findSubComponents(comp);
            List<T> out = new ArrayList<>();
            for (Class<?> c : clz) out.addAll(instancesOf(c, args));
            return out;
        } catch (ClassNotFoundException e) { return Collections.emptyList(); }
    }

    /* -------------------------------------------------------------------- */
    /*  PLATFORM-SPECIFIC LOOK-UP                                           */
    /* -------------------------------------------------------------------- */
    @Override
    public <T> T tryCreate(ComponentNames comp, PlatformNames platform, Object... args) {
        try {
            for (Class<?> c : findClasses(comp)) {
                if (Objects.requireNonNull(IClassLoader.resolvedInit(c)).platform() == platform)
                    return firstInstanceOf(c, args);
            }
        } catch (ClassNotFoundException ignored) { }
        throw new IllegalStateException("No "+comp+" for "+platform);
    }


    /* ==================================================================== */
    /*  Reflection convenience                                              */
    /* ==================================================================== */
    @Override
    public Object createInstance(Class<?> clazz, Object... params) {
        // If the concrete class says “external”, always use its own factory.
        if (clazz.getAnnotation(Init.class).external()) {
            return newViaExternalFactory(clazz);
        }

        // Cache a single instance per class if NONE of the implemented IBaseComp interfaces allow multiple.
        boolean noneAllowMultiple = Arrays.stream(clazz.getInterfaces())
            .filter(IBaseComp.class::isAssignableFrom)
            .map(IClassLoader::resolvedInit)
            .noneMatch(Init::allowMultiple);

        if (noneAllowMultiple) {
            return singletons.computeIfAbsent(clazz, c -> newViaConstructor(c, params));
        }

        // Otherwise return a fresh instance.
        return newViaConstructor(clazz, params);
    }

    private Object newViaExternalFactory(Class<?> clazz) {
        try {
            return clazz.getMethod("getInstance").invoke(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                "Class " + clazz.getName() +
                    " declares @Init(external=true) but lacks a public static getInstance()", e);
        }
    }

    // helper – keep your existing constructor-selection logic here
    private Object newViaConstructor(Class<?> clazz, Object... params) {
        try {
            Constructor<?> ctor;
            if (params.length == 0) {
                ctor = clazz.getDeclaredConstructor();
            } else {
                try {
                    Class<?>[] sig = Arrays.stream(params).map(Object::getClass).toArray(Class<?>[]::new);
                    ctor = clazz.getDeclaredConstructor(sig);
                } catch (NoSuchMethodException e) {
                    ctor = clazz.getDeclaredConstructor(); // fallback
                    params = new Object[0];
                }
            }
            ctor.setAccessible(true);
            return ctor.newInstance(params);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot instantiate " + clazz.getName(), e);
        }
    }

    /* ==================================================================== */
    /*  Enum helpers                                                        */
    /* ==================================================================== */

    /** Returns all enum constants or a singleton list with one normal instance. */
    @SuppressWarnings("unchecked")
    private <T> List<T> instancesOf(Class<?> c, Object... ctorArgs) {
        if (c.isEnum()) {
            return Arrays.stream(c.getEnumConstants())
                .map(e -> {
                    if (e instanceof IBaseComp bc) {
                        ComponentUtils.assignEnum(bc); // global, unique; stored via setMultId
                    }
                    return (T) e;
                })
                .toList();
        }
        return List.of((T) createInstance(c, ctorArgs));
    }



    /** First constant of an enum – convenient for single-instance look-ups. */
    @SuppressWarnings("unchecked")
    private <T> T firstInstanceOf(Class<?> c, Object... ctorArgs) {
        if (c.isEnum())
            return (T) c.getEnumConstants()[0];
        return (T) createInstance(c, ctorArgs);
    }

    /** Convenience method */
    @Override
    public <T extends IBaseComp> Optional<T> lookup(int id, Class<T> type) {
        return ComponentUtils.lookup(id).map(type::cast);
    }

    @Override
    public Class<?> findNextLowerFor(ComponentNames comp,
                                     SubComponentNames sub,
                                     int currentPriority) throws ClassNotFoundException {
        ensureTypesLoaded();
        final String k = IClassLoader.keyOf(comp, sub);
        final List<Class<?>> chain = allConcreteByKey.get(k);

        if (chain == null || chain.isEmpty()) {
            throw new ClassNotFoundException("No implementations registered for " + comp + "/" + sub);
        }

        // chain is DESC by priority, so the first with p < currentPriority is the closest lower
        for (Class<?> candidate : chain) {
            final Init meta = IClassLoader.resolvedInit(candidate);
            final int p = meta.module().modulePriority;
            if (p < currentPriority) {
                log.debug("findNextLowerFor({}, {}, prio={}): {} (prio={})",
                    comp, sub, currentPriority, candidate.getName(), p);
                return candidate;
            }
        }

        throw new ClassNotFoundException(
            "No lower implementation below priority " + currentPriority + " for " + comp + "/" + sub);
    }

    /** Convenience overload if you have the current module enum. */
    @Override
    public Class<?> findNextLowerFor(ComponentNames comp,
                                     SubComponentNames sub,
                                     ModuleNames currentModule) throws ClassNotFoundException {
        return findNextLowerFor(comp, sub, currentModule.modulePriority);
    }

    /** Convenience overload when you already have a @Init-bearing class loaded. */
    @Override
    public Class<?> findNextLowerFor(Class<?> currentClass) throws ClassNotFoundException {
        final Init cur = IClassLoader.resolvedInit(currentClass);
        return findNextLowerFor(cur.component(), cur.subComp(), cur.module().modulePriority);
    }

    @Override
    public List<Class<?>> listSubComponents(ComponentNames comp) {
        try { return findSubComponents(comp); }
        catch (ClassNotFoundException e) { return java.util.Collections.emptyList(); }
    }
    @Override
    public List<Class<?>> listSubComponents(ComponentNames comp, Class<?> mustImplement) {
        try {
            List<Class<?>> all = findSubComponents(comp);
            List<Class<?>> out = new ArrayList<>();
            for (Class<?> c : all) if (mustImplement.isAssignableFrom(c)) out.add(c);
            return out;
        } catch (ClassNotFoundException e) { return java.util.Collections.emptyList(); }
    }

    /* ==================================================================== */
    /*  Helper DTO                                                          */
    /* ==================================================================== */
    public record ProjectLoader(URLClassLoader cl, int level) { }
    public List<ProjectLoader> getClassLoaders() { return classLoaders; }
}
