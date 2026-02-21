package com.gwngames.core.base.cfg;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.gwngames.core.CoreModule;
import com.gwngames.core.CoreSubComponent;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.base.cfg.IApplicationLogger;
import com.gwngames.core.api.base.cfg.IClassLoader;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.ex.ErrorPopupException;
import com.gwngames.core.base.cfg.i18n.CoreTranslation;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;
import com.gwngames.core.generated.ModulePriorityRegistry;
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
 * Dynamic class-loader for GW Framework modules (String-based catalogs).
 * <br>
 * Keys are formed as: "component#subComp" (subComp can be NONE).
 * Module priority is resolved via {@link ModulePriorityRegistry#priorityOf(String)}.
 */
@Init(module = CoreModule.CORE) // or DefaultModule.CORE if accessible from core
public class ModuleClassLoader extends ClassLoader implements IClassLoader {
    private static FileLogger log;
    /* ───────────────────────── static members ───────────────────────── */
    /** Interfaces annotated with @Init(module=INTERFACE) (you filter by IBaseComp). */
    private static final List<Class<?>> interfaceTypes = new ArrayList<>();
    /** Concrete classes kept after duplicate-pruning. */
    private static final List<Class<?>> concreteTypes  = new ArrayList<>();

    private static FileLogger log() {
        if (log == null) {
            log = FileLogger.get(LogFiles.SYSTEM, true);
        } else {
            log.setForceDefaultLog(false);
        }
        return log;
    }
    /**
     * All candidates per key "component#sub" (sub can be NONE), sorted by priority DESC.
     * Used for "next lower" selection.
     */
    private final Map<String, List<Class<?>>> allConcreteByKey = new ConcurrentHashMap<>();

    private final Map<Class<?>, Object> singletons = new ConcurrentHashMap<>();

    private static ModuleClassLoader INSTANCE;

    /* positive / negative lookup caches */
    private final Map<String, Class<?>> resolved = new ConcurrentHashMap<>();
    private final Set<String>           misses   = ConcurrentHashMap.newKeySet();

    /* per-instance state */
    private final List<URLClassLoader> loaders      = new ArrayList<>();
    private final Map<URLClassLoader, JarFile> jars = new LinkedHashMap<>();
    private final List<URLClassLoader> dirLoaders   = new ArrayList<>();
    private final List<ProjectLoader>  classLoaders = new ArrayList<>();

    /* ==================================================================== */
    /*  Singleton                                                           */
    /* ==================================================================== */
    public static synchronized ModuleClassLoader getInstance() {
        if (INSTANCE == null) {
            try {
                log().info("Initializing ModuleClassLoader singleton...");
                INSTANCE = new ModuleClassLoader();
                INSTANCE.ensureTypesLoaded();
                log().info("ModuleClassLoader initialized successfully.");
            } catch (ErrorPopupException e) {
                log().error("Failed to initialize ModuleClassLoader", e);
                throw new RuntimeException(e);
            }
        }
        return INSTANCE;
    }

    private ModuleClassLoader() throws ErrorPopupException {
        super(ModuleClassLoader.class.getClassLoader());
        log().debug("ModuleClassLoader created with parent ClassLoader: {}", getParent());
        initLoaders();
        initJars();
        log().info("ModuleClassLoader setup completed with {} class loaders and {} JARs.",
            loaders.size(), jars.size());
    }

    /* ==================================================================== */
    /*  Helpers (String ids + priority)                                     */
    /* ==================================================================== */

    private static String norm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean eqId(String a, String b) {
        return norm(a).equals(norm(b));
    }

    private static boolean isNoneSub(String sub) {
        return sub == null || sub.isBlank() || eqId(sub, CoreSubComponent.NONE);
    }

    private static String keyOf(String component, String subComp) {
        return norm(component) + "#" + norm(subComp);
    }

    private static int prioOf(Init an) {
        return ModulePriorityRegistry.priorityOf(an.module());
    }

    /* ==================================================================== */
    /*  Loader / JAR discovery                                              */
    /* ==================================================================== */

    private void initLoaders() throws ErrorPopupException {
        String forcedBin = System.getProperty("gw.bin.dir");
        if (forcedBin != null) {
            log().info("Using forced binary directory: {}", forcedBin);
            useBinDir(new File(forcedBin));
            return;
        }

        File whereAmI;
        try {
            URL src = getClass().getProtectionDomain().getCodeSource().getLocation();
            whereAmI = new File(src.toURI());
            log().debug("ModuleClassLoader source location: {}", src);
            if (whereAmI.isFile()) whereAmI = whereAmI.getParentFile();
        } catch (Exception e) {
            log().error("Executable location not found.", e);
            throw new ErrorPopupException(CoreTranslation.EXE_NOT_FOUND);
        }

        File binDir;
        if (new File(whereAmI, "config.json").isFile()) {
            binDir = whereAmI;
        } else if ("lib".equals(whereAmI.getName())) {
            binDir = new File(whereAmI.getParentFile(), "bin");
        } else {
            log().error("Binary directory not found near: {}", whereAmI);
            throw new ErrorPopupException(CoreTranslation.BIN_NOT_FOUND, whereAmI.toString());
        }

        log().info("Using binary directory: {}", binDir);
        useBinDir(binDir);
    }

    @SuppressWarnings("unchecked")
    private void useBinDir(File binDir) throws ErrorPopupException {
        Map<String, Object> cfg;
        File cfgFile = new File(binDir, "config.json");
        try (JsonReader r = new JsonReader(new FileReader(cfgFile))) {
            log().debug("Reading configuration from {}", cfgFile);
            cfg = new Gson().fromJson(r, new TypeToken<Map<String,Object>>(){}.getType());
        } catch (Exception e) {
            log().error("Configuration file not found: {}", cfgFile, e);
            throw new ErrorPopupException(CoreTranslation.CONFIG_NOT_FOUND);
        }

        String gameType = (String) cfg.get("gameType");
        List<Map<String,Object>> projects = (List<Map<String,Object>>) cfg.get("projects");
        if (projects == null || projects.isEmpty()) {
            log().error("No projects found in configuration.");
            throw new ErrorPopupException(CoreTranslation.PROJECTS_NOT_FOUND);
        }

        File root = binDir.getParentFile();
        int loaderCount = 0;
        for (Map<String,Object> p : projects) {
            if (!Objects.equals(gameType, p.get("type"))) continue;

            File jar = new File(root, String.valueOf(p.get("jar")));
            if (!jar.exists()) continue;

            try {
                URLClassLoader cl = new TransformingURLClassLoader(
                    jar.getName(),
                    new URL[]{jar.toURI().toURL()},
                    this,
                    this
                );
                loaders.add(cl);

                int level = 0;
                Object lvlObj = p.get("level");
                if (lvlObj != null) {
                    try { level = (int) Double.parseDouble(String.valueOf(lvlObj)); }
                    catch (Exception ignored) {}
                }
                classLoaders.add(new ProjectLoader(cl, level));
                loaderCount++;
            } catch (MalformedURLException e) {
                log().error("JAR not found: {}", jar, e);
                throw new ErrorPopupException(CoreTranslation.JAR_NOT_FOUND, jar.toString());
            }
        }

        loaders.sort(Comparator.comparing(ClassLoader::getName));
        log().info("{} project class loaders initialized.", loaderCount);
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
                        log().error("Error opening JAR file {}", f, e);
                    }
                }
            }
        }
        log().info("Initialized {} JAR files and {} class directories.", jarCount, dirCount);
    }

    /* ==================================================================== */
    /*  Class-loading override – child-first with caches                    */
    /* ==================================================================== */

    @Override
    public Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> hit = resolved.get(name);
        if (hit != null) return hit;
        if (misses.contains(name)) throw new ClassNotFoundException(name);

        final String path = name.replace('.', '/') + ".class";

        for (URLClassLoader l : loaders) {
            try {
                if (l.findResource(path) == null) continue;

                Class<?> c = Class.forName(name, false, l);
                resolved.put(name, c);
                log().debug("Class {} loaded by {}", name, l.getName());
                return c;
            } catch (ClassNotFoundException ignore) {
                // try next
            }
        }

        misses.add(name);
        log().error("Class {} not found.", name);
        throw new ClassNotFoundException(name);
    }

    /* ==================================================================== */
    /*  Annotation scanning & de-duplication                                */
    /* ==================================================================== */

    private synchronized void ensureTypesLoaded() {
        if (!interfaceTypes.isEmpty() || !concreteTypes.isEmpty()) return;

        log().info("Scanning for @Init-annotated types …");
        List<Class<?>> found = scanForAnnotated(Init.class);
        log().info("Found {} @Init-annotated classes.", found.size());

        Map<String, Class<?>> bestMulti = new HashMap<>();
        Map<String, List<Class<?>>> allByKeyTmp = new HashMap<>();

        for (Class<?> c : found) {
            Init an = IClassLoader.resolvedInit(c);

            if (c.isInterface()) {
                if (IBaseComp.class.isAssignableFrom(c)) {
                    interfaceTypes.add(c);
                } else {
                    log().debug("Skipping interface {} – does not extend IBaseComp", c.getName());
                }
                continue;
            }

            if (!IBaseComp.class.isAssignableFrom(c)) continue;

            String groupKey = keyOf(an.component(), an.subComp());
            allByKeyTmp.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(c);

            if (isNoneSub(an.subComp())) {
                concreteTypes.add(c);
                continue;
            }

            // Pick highest-priority implementation per (component, subComp)
            String k = keyOf(an.component(), an.subComp());
            Class<?> incumbent = bestMulti.get(k);
            if (incumbent == null) {
                bestMulti.put(k, c);
            } else {
                int pNew = prioOf(an);
                int pOld = prioOf(IClassLoader.resolvedInit(incumbent));
                if (pNew > pOld) bestMulti.put(k, c);
            }
        }

        concreteTypes.addAll(bestMulti.values());
        log().info("{} interface types and {} concrete types registered.", interfaceTypes.size(), concreteTypes.size());

        // Sort & freeze the all-candidates index by module priority DESC
        for (Map.Entry<String, List<Class<?>>> e : allByKeyTmp.entrySet()) {
            e.getValue().sort(Comparator.comparingInt(
                (Class<?> cl) -> prioOf(IClassLoader.resolvedInit(cl))
            ).reversed());
            allConcreteByKey.put(e.getKey(), List.copyOf(e.getValue()));
        }

        // Enum pre-assignment stays
        for (Class<?> c : concreteTypes) {
            if (c.isEnum()) {
                instancesOf(c);
                log().debug("Added enum: {}", c.getSimpleName());
            }
        }
    }

    @Override
    public List<Class<?>> scanForAnnotated(Class<? extends Annotation> ann) {
        List<Class<?>> out = new ArrayList<>();

        // 1) JARs
        jars.forEach((loader, jar) -> jar.entries().asIterator().forEachRemaining(e -> {
            if (!e.getName().endsWith(".class")) return;
            String cn = e.getName().replace('/', '.').replace(".class", "");
            try {
                Class<?> c = loader.loadClass(cn);
                if (c.getAnnotation(ann) != null) out.add(c);
            } catch (Throwable ignored) {}
        }));

        // 2) Directories
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
    /*  Lookup helpers (String ids)                                         */
    /* ==================================================================== */

    private Class<?> interfaceOf(String componentId) throws ClassNotFoundException {
        ensureTypesLoaded();
        for (Class<?> i : interfaceTypes) {
            Init an = i.getAnnotation(Init.class);
            if (an != null && eqId(an.component(), componentId)) return i;
        }
        throw new ClassNotFoundException("No interface for component " + componentId);
    }

    /* single-component (allowMultiple = false) --------------------------- */

    public Class<?> _findClass(String componentId) throws ClassNotFoundException {
        Class<?> iface = interfaceOf(componentId);

        Class<?> best = null;
        int bestPrio = Integer.MIN_VALUE;

        for (Class<?> c : concreteTypes) {
            if (!iface.isAssignableFrom(c)) continue;

            Init an = IClassLoader.resolvedInit(c);
            if (!isNoneSub(an.subComp())) continue;

            int p = prioOf(an);
            if (p > bestPrio) { best = c; bestPrio = p; }
        }

        if (best == null) throw new ClassNotFoundException("No impl of " + componentId);
        return best;
    }

    public List<Class<?>> findClasses(String componentId) throws ClassNotFoundException {
        Class<?> iface = interfaceOf(componentId);
        List<Class<?>> out = new ArrayList<>();

        for (Class<?> c : concreteTypes) {
            if (!iface.isAssignableFrom(c)) continue;

            Init an = IClassLoader.resolvedInit(c);
            if (!isNoneSub(an.subComp())) continue;

            out.add(c);
        }

        if (out.isEmpty()) throw new ClassNotFoundException("No impl of " + componentId);

        out.sort(Comparator.comparingInt(
            (Class<?> cl) -> prioOf(IClassLoader.resolvedInit(cl))
        ).reversed());

        return out;
    }

    /* multi sub-components (allowMultiple = true) ------------------------ */

    public Class<?> findSubComponent(String componentId, String subCompId) throws ClassNotFoundException {
        if (isNoneSub(subCompId)) throw new IllegalArgumentException("subComp NONE");

        Class<?> iface = interfaceOf(componentId);
        Init ifaceAnn = iface.getAnnotation(Init.class);
        if (ifaceAnn == null || !ifaceAnn.allowMultiple()) {
            throw new IllegalStateException(iface.getSimpleName() + " does not allow multiple");
        }

        Class<?> best = null;
        int bestPrio = Integer.MIN_VALUE;

        for (Class<?> c : concreteTypes) {
            Init an = IClassLoader.resolvedInit(c);

            if (!eqId(an.subComp(), subCompId)) continue;
            if (!iface.isAssignableFrom(c)) continue;

            int p = prioOf(an);
            if (p > bestPrio) { best = c; bestPrio = p; }
        }

        if (best == null) throw new ClassNotFoundException("No " + componentId + "/" + subCompId);
        return best;
    }

    private List<Class<?>> findSubComponents(String componentId) throws ClassNotFoundException {
        Class<?> iface = interfaceOf(componentId);
        Init ifaceAnn = iface.getAnnotation(Init.class);
        if (ifaceAnn == null || !ifaceAnn.allowMultiple()) {
            throw new IllegalStateException(iface.getSimpleName() + " does not allow multiple");
        }

        List<Class<?>> out = new ArrayList<>();
        for (Class<?> c : concreteTypes) {
            Init an = IClassLoader.resolvedInit(c);

            if (iface.isAssignableFrom(c) && !isNoneSub(an.subComp())) out.add(c);
        }

        if (out.isEmpty()) throw new ClassNotFoundException("No sub-components for " + componentId);

        out.sort(Comparator.comparingInt(
            (Class<?> cl) -> prioOf(IClassLoader.resolvedInit(cl))
        ).reversed());

        return out;
    }

    /* ==================================================================== */
    /*  Public creation helpers (String ids)                                */
    /* ==================================================================== */

    public <T> T tryCreate(String componentId, Object... args) {
        try { return firstInstanceOf(_findClass(componentId), args); }
        catch (ClassNotFoundException e) {
            log().error(e.getMessage());
            return null;
        }
    }

    public <T> T tryCreate(String componentId, String subCompId, Object... args) {
        try {
            T obj = firstInstanceOf(findSubComponent(componentId, subCompId), args);
            if (obj instanceof IBaseComp bc) {
                log().debug("Created {} id={}", bc.getClass().getSimpleName(), bc.getMultId());
            }
            return obj;
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public <T> List<T> tryCreateAll(String componentId, Class<?> mustImplement, Object... args) {
        if (mustImplement == null || !mustImplement.isInterface()) {
            throw new IllegalStateException("mustImplement must be a non-null interface");
        }
        try {
            List<Class<?>> clz = findSubComponents(componentId);
            List<T> out = new ArrayList<>();
            for (Class<?> c : clz) {
                if (mustImplement.isAssignableFrom(c)) {
                    out.addAll(instancesOf(c, args));
                }
            }
            return out;
        } catch (ClassNotFoundException e) {
            return Collections.emptyList();
        }
    }

    public <T> List<T> tryCreateAll(String componentId, Object... args) {
        try {
            List<Class<?>> clz = findSubComponents(componentId);
            List<T> out = new ArrayList<>();
            for (Class<?> c : clz) out.addAll(instancesOf(c, args));
            return out;
        } catch (ClassNotFoundException e) {
            return Collections.emptyList();
        }
    }

    public <T> T tryCreateSpecific(String componentId, String platformId, Object... args) {
        try {
            for (Class<?> c : findClasses(componentId)) {
                Init an = IClassLoader.resolvedInit(c);
                if (eqId(an.platform(), platformId)) {
                    return firstInstanceOf(c, args);
                }
            }
        } catch (ClassNotFoundException ignored) { }
        throw new IllegalStateException("No " + componentId + " for " + platformId);
    }

    /* ==================================================================== */
    /*  Reflection convenience                                              */
    /* ==================================================================== */

    @Override
    public Object createInstance(Class<?> clazz, Object... params) {
        Init meta = clazz.getAnnotation(Init.class);
        if (meta != null && meta.external()) {
            return newViaExternalFactory(clazz);
        }

        boolean noneAllowMultiple = Arrays.stream(clazz.getInterfaces())
            .filter(IBaseComp.class::isAssignableFrom)
            .map(IClassLoader::resolvedInit)
            .noneMatch(Init::allowMultiple);

        if (noneAllowMultiple) {
            return singletons.computeIfAbsent(clazz, c -> newViaConstructor(c, params));
        }

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
                    ctor = clazz.getDeclaredConstructor();
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

    @SuppressWarnings("unchecked")
    private <T> List<T> instancesOf(Class<?> c, Object... ctorArgs) {
        if (c.isEnum()) {
            return Arrays.stream(c.getEnumConstants())
                .map(e -> {
                    if (e instanceof IBaseComp bc) {
                        ComponentUtils.assignEnum(bc);
                    }
                    return (T) e;
                })
                .toList();
        }
        return List.of((T) createInstance(c, ctorArgs));
    }

    @SuppressWarnings("unchecked")
    private <T> T firstInstanceOf(Class<?> c, Object... ctorArgs) {
        if (c.isEnum()) return (T) c.getEnumConstants()[0];
        return (T) createInstance(c, ctorArgs);
    }

    @Override
    public <T extends IBaseComp> Optional<T> lookup(int id, Class<T> type) {
        return ComponentUtils.lookup(id).map(type::cast);
    }

    /* ==================================================================== */
    /*  Next-lower lookup (String ids)                                      */
    /* ==================================================================== */

    public Class<?> findNextLowerFor(String componentId, String subCompId, int currentPriority)
        throws ClassNotFoundException {

        ensureTypesLoaded();

        final String k = keyOf(componentId, subCompId);
        final List<Class<?>> chain = allConcreteByKey.get(k);

        if (chain == null || chain.isEmpty()) {
            throw new ClassNotFoundException("No implementations registered for " + componentId + "/" + subCompId);
        }

        for (Class<?> candidate : chain) {
            final Init meta = IClassLoader.resolvedInit(candidate);
            final int p = ModulePriorityRegistry.priorityOf(meta.module());
            if (p < currentPriority) {
                log().debug("findNextLowerFor({}, {}, prio={}): {} (prio={})",
                    componentId, subCompId, currentPriority, candidate.getName(), p);
                return candidate;
            }
        }

        throw new ClassNotFoundException(
            "No lower implementation below priority " + currentPriority + " for " + componentId + "/" + subCompId);
    }

    public Class<?> findNextLowerFor(String componentId, String subCompId, String currentModuleId)
        throws ClassNotFoundException {
        return findNextLowerFor(componentId, subCompId, ModulePriorityRegistry.priorityOf(currentModuleId));
    }

    public Class<?> findNextLowerFor(Class<?> currentClass) throws ClassNotFoundException {
        final Init cur = IClassLoader.resolvedInit(currentClass);
        return findNextLowerFor(cur.component(), cur.subComp(), ModulePriorityRegistry.priorityOf(cur.module()));
    }

    public List<Class<?>> listSubComponents(String componentId) {
        try { return findSubComponents(componentId); }
        catch (ClassNotFoundException e) { return Collections.emptyList(); }
    }

    public List<Class<?>> listSubComponents(String componentId, Class<?> mustImplement) {
        try {
            List<Class<?>> all = findSubComponents(componentId);
            List<Class<?>> out = new ArrayList<>();
            for (Class<?> c : all) if (mustImplement.isAssignableFrom(c)) out.add(c);
            return out;
        } catch (ClassNotFoundException e) {
            return Collections.emptyList();
        }
    }

    /* ==================================================================== */
    /*  Helper DTO                                                          */
    /* ==================================================================== */
    public record ProjectLoader(URLClassLoader cl, int level) { }
    public List<ProjectLoader> getClassLoaders() { return classLoaders; }

}
