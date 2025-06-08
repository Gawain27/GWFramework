package com.gwngames.core.base.cfg;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.ex.ErrorPopupException;
import com.gwngames.core.base.cfg.i18n.CoreTranslation;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.*;

import java.io.*;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;

/**
 * Dynamic class-loader for GW Framework modules.
 * <p>
 * ➡️ **New feature:** If a concrete class annotated with {@link Init}
 * specifies <em>subComp ≠ NONE</em> but leaves <code>component()</code> and/or
 * <code>module()</code> as the sentinel value <strong>AUTO</strong>,
 * those missing attributes are automatically inherited from the first
 * superclass that declares them.  This is resolved at runtime – no byte-code
 * changes are made.
 * </p>
 */
public final class ModuleClassLoader extends ClassLoader {

    /* ───────────────────────── static members ───────────────────────── */
    private static final FileLogger log = FileLogger.get(LogFiles.SYSTEM);

    /** Interfaces annotated with <code>@Init(module = INTERFACE)</code>. */
    private static final List<Class<?>> interfaceTypes = new ArrayList<>();
    /** Concrete classes kept after duplicate-pruning. */
    private static final List<Class<?>> concreteTypes  = new ArrayList<>();

    private static ModuleClassLoader INSTANCE;

    /* positive / negative lookup caches */
    private final Map<String, Class<?>> resolved = new ConcurrentHashMap<>();
    private final Set<String>           misses   = ConcurrentHashMap.newKeySet();

    /* per-instance state */
    private final List<URLClassLoader> loaders      = new ArrayList<>();
    private final Map<URLClassLoader, JarFile> jars = new LinkedHashMap<>();
    private final List<ProjectLoader> classLoaders = new ArrayList<>();

    /* ==================================================================== */
    /*  Singleton                                                           */
    /* ==================================================================== */
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

    private ModuleClassLoader() throws ErrorPopupException {
        super(ModuleClassLoader.class.getClassLoader());
        initLoaders();
        initJars();
    }

    /* ==================================================================== */
    /*  Loader / JAR discovery                                              */
    /* ==================================================================== */

    private void initLoaders() throws ErrorPopupException {
        /* test override ---------------------------------------------------- */
        String forcedBin = System.getProperty("gw.bin.dir");
        if (forcedBin != null) {
            useBinDir(new File(forcedBin));
            return;
        }

        /* normal runtime --------------------------------------------------- */
        File whereAmI;
        try {
            URL src = getClass().getProtectionDomain().getCodeSource().getLocation();
            whereAmI = new File(src.toURI());
            if (whereAmI.isFile()) whereAmI = whereAmI.getParentFile();
        } catch (Exception e) {
            throw new ErrorPopupException(CoreTranslation.EXE_NOT_FOUND);
        }

        File binDir;
        if (new File(whereAmI, "config.json").isFile()) {
            binDir = whereAmI;
        } else if ("lib".equals(whereAmI.getName())) {
            binDir = new File(whereAmI.getParentFile(), "bin");
        } else {
            throw new ErrorPopupException(CoreTranslation.BIN_NOT_FOUND, whereAmI.toString());
        }
        useBinDir(binDir);
    }

    @SuppressWarnings("unchecked")
    private void useBinDir(File binDir) throws ErrorPopupException {
        /* parse config ----------------------------------------------------- */
        Map<String, Object> cfg;
        try (JsonReader r = new JsonReader(new FileReader(new File(binDir, "config.json")))) {
            cfg = new Gson().fromJson(r, new TypeToken<Map<String,Object>>(){}.getType());
        } catch (Exception e) {
            throw new ErrorPopupException(CoreTranslation.CONFIG_NOT_FOUND);
        }

        String gameType = (String) cfg.get("gameType");
        List<Map<String,Object>> projects = (List<Map<String,Object>>) cfg.get("projects");
        if (projects == null || projects.isEmpty())
            throw new ErrorPopupException(CoreTranslation.PROJECTS_NOT_FOUND);

        File root = binDir.getParentFile();
        for (Map<String,Object> p : projects) {
            if (!gameType.equals(p.get("type"))) continue;
            File jar = new File(root, (String) p.get("jar"));
            if (!jar.exists()) continue;
            try {
                URLClassLoader cl = createNamedLoader(jar);
                loaders.add(cl);
                log.debug("loader '{}' (level {})", cl.getName(), p.get("level"));
                classLoaders.add(new ProjectLoader(cl, (int) Double.parseDouble(String.valueOf(p.get("level")))));
            } catch (MalformedURLException e) {
                throw new ErrorPopupException(CoreTranslation.JAR_NOT_FOUND, jar.toString());
            }
        }
        loaders.sort(Comparator.comparing(ClassLoader::getName)); // already ordered by filename/level
    }

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

    private void initJars() {
        loaders.forEach(l -> {
            for (URL u : l.getURLs()) {
                File f = new File(u.getFile());
                if (!f.getName().endsWith(".jar")) continue;
                try { jars.put(l, new JarFile(f)); }
                catch (IOException e) { log.error("Error opening {}", f, e); }
            }
        });
    }

    /* ==================================================================== */
    /*  Class-loading override – child-first with caches                    */
    /* ==================================================================== */

    @Override protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> hit = resolved.get(name);
        if (hit != null) return hit;
        if (misses.contains(name)) throw new ClassNotFoundException(name);

        for (URLClassLoader l : loaders) {
            try {
                hit = Class.forName(name, false, l);
                resolved.put(name, hit);
                return hit;
            } catch (ClassNotFoundException ignored) { }
        }
        misses.add(name);
        throw new ClassNotFoundException(name);
    }

    /* ==================================================================== */
    /*  Init-annotation inheritance helper                                  */
    /* ==================================================================== */

    /**
     * Produces a merged {@link Init} where missing <code>component()</code> or
     * <code>module()</code> values (sentinel {@link ComponentNames#NONE} /
     * {@link ModuleNames#UNIMPLEMENTED}) are inherited from the nearest superclass that
     * specifies them.
     */
    private static Init resolvedInit(Class<?> clazz) {
        Init base = clazz.getAnnotation(Init.class);
        if (base == null) return null;

        ComponentNames comp   = base.component();
        ModuleNames    module = base.module();

        if (comp != ComponentNames.NONE && module != ModuleNames.UNIMPLEMENTED)
            return base;                      // nothing to fill

        Class<?> sup = clazz.getSuperclass();
        while (sup != null && sup != Object.class) {
            Init supAnn = sup.getAnnotation(Init.class);
            if (supAnn != null) {
                if (comp   == ComponentNames.NONE) comp   = supAnn.component();
                if (module == ModuleNames.UNIMPLEMENTED)    module = supAnn.module();
                if (comp != ComponentNames.NONE && module != ModuleNames.UNIMPLEMENTED)
                    break;
            }
            sup = sup.getSuperclass();
        }

        ComponentNames finalComp   = comp;
        ModuleNames    finalModule = module;

        /* dynamic proxy implementing merged Init                            */
        return (Init) Proxy.newProxyInstance(
            Init.class.getClassLoader(),
            new Class<?>[]{Init.class},
            (p, m, args) -> switch (m.getName()) {
                case "annotationType" -> Init.class;
                case "component"      -> finalComp;
                case "module"         -> finalModule;
                case "subComp"        -> base.subComp();
                case "platform"       -> base.platform();
                case "allowMultiple"  -> base.allowMultiple();
                default -> m.invoke(base, args);
            });
    }

    /* ==================================================================== */
    /*  Annotation scanning & de-duplication                                */
    /* ==================================================================== */

    private synchronized void ensureTypesLoaded() {
        if (!interfaceTypes.isEmpty() || !concreteTypes.isEmpty()) return;

        List<Class<?>> found = scanForAnnotated(Init.class);

        Map<String, Class<?>> bestMulti = new HashMap<>();

        for (Class<?> c : found) {
            Init an = resolvedInit(c);                 // 🔸 merged Init
            if (an == null) continue;

            if (c.isInterface()) {
                interfaceTypes.add(c);
                continue;
            }

            if (an.subComp() == SubComponentNames.NONE) {
                concreteTypes.add(c);                  // single component impl
                continue;
            }

            String key = an.component().name() + "#" + an.subComp().name();
            Class<?> incumbent = bestMulti.get(key);
            if (incumbent == null ||
                an.module().modulePriority >
                    resolvedInit(incumbent).module().modulePriority) {
                bestMulti.put(key, c);
            }
        }
        concreteTypes.addAll(bestMulti.values());

        bestMulti.forEach((k, cls) -> {
            Init an = resolvedInit(cls);
            log.info("Multi-component [{}] → {} (prio {})",
                k, cls.getSimpleName(), an.module().modulePriority);
        });
    }

    private List<Class<?>> scanForAnnotated(Class<? extends Annotation> ann) {
        List<Class<?>> out = new ArrayList<>();
        jars.forEach((loader, jar) ->
            jar.entries().asIterator().forEachRemaining(e -> {
                if (!e.getName().endsWith(".class")) return;
                String cn = e.getName().replace('/', '.').replace(".class", "");
                try {
                    Class<?> c = loader.loadClass(cn);
                    if (c.getAnnotation(ann) != null) out.add(c);
                } catch (Throwable ignored) { }
            }));
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
    private Class<?> findClass(ComponentNames comp) throws ClassNotFoundException {
        Class<?> iface = interfaceOf(comp);
        Class<?> best  = null; int prio = Integer.MIN_VALUE;
        for (Class<?> c : concreteTypes) {
            if (!iface.isAssignableFrom(c)) continue;
            Init an = resolvedInit(c);
            if (an.subComp() != SubComponentNames.NONE) continue;
            if (an.module().modulePriority > prio) { best = c; prio = an.module().modulePriority; }
        }
        if (best == null) throw new ClassNotFoundException("No impl of " + comp);
        return best;
    }

    private List<Class<?>> findClasses(ComponentNames comp) throws ClassNotFoundException {
        Class<?> iface = interfaceOf(comp);
        List<Class<?>> out = new ArrayList<>();
        for (Class<?> c : concreteTypes) {
            if (!iface.isAssignableFrom(c)) continue;
            Init an = resolvedInit(c);
            if (an.subComp() != SubComponentNames.NONE) continue;
            out.add(c);
        }
        if (out.isEmpty()) throw new ClassNotFoundException("No impl of " + comp);
        out.sort(Comparator.comparingInt(
            (Class<?> cl) -> resolvedInit(cl).module().modulePriority).reversed());
        return out;
    }

    /* multi sub-components (allowMultiple = true) ------------------------ */
    private Class<?> findSubComponent(ComponentNames comp, SubComponentNames sub)
        throws ClassNotFoundException {

        if (sub == SubComponentNames.NONE) throw new IllegalArgumentException("subComp NONE");
        Class<?> iface = interfaceOf(comp);
        Init ifaceAnn  = iface.getAnnotation(Init.class);
        if (!ifaceAnn.allowMultiple())
            throw new IllegalStateException(iface.getSimpleName()+" does not allow multiple");

        Class<?> best = null; int prio = Integer.MIN_VALUE;
        for (Class<?> c : concreteTypes) {
            Init an = resolvedInit(c);
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
            Init an = resolvedInit(c);
            if (iface.isAssignableFrom(c) && an.subComp()!=SubComponentNames.NONE)
                out.add(c);
        }
        if (out.isEmpty()) throw new ClassNotFoundException("No sub-components for "+comp);
        out.sort(Comparator.comparingInt(
            (Class<?> cl) -> resolvedInit(cl).module().modulePriority).reversed());
        return out;
    }

    /* -------------------------------------------------------------------- */
    /*  SIMPLE COMPONENT (one impl only)                                    */
    /* -------------------------------------------------------------------- */
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
    public <T> T tryCreate(ComponentNames comp, PlatformNames platform, Object... args) {
        try {
            for (Class<?> c : findClasses(comp)) {
                if (resolvedInit(c).platform() == platform)
                    return firstInstanceOf(c, args);
            }
        } catch (ClassNotFoundException ignored) { }
        throw new IllegalStateException("No "+comp+" for "+platform);
    }


    /* ==================================================================== */
    /*  Reflection convenience                                              */
    /* ==================================================================== */

    public Object createInstance(Class<?> clazz, Object... params) {
        try {
            Constructor<?> ctor;
            if (params.length==0) ctor = clazz.getDeclaredConstructor();
            else try {
                Class<?>[] sig = Arrays.stream(params).map(Object::getClass).toArray(Class<?>[]::new);
                ctor = clazz.getDeclaredConstructor(sig);
            } catch (NoSuchMethodException e) {
                ctor = clazz.getDeclaredConstructor(); params = new Object[0];
            }
            ctor.setAccessible(true);
            return ctor.newInstance(params);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot instantiate "+clazz.getName(), e);
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
                .map(o -> (T) o)
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


    /* ==================================================================== */
    /*  Helper DTO                                                          */
    /* ==================================================================== */
    public record ProjectLoader(URLClassLoader cl, int level) { }
    public List<ProjectLoader> getClassLoaders() { return classLoaders; }
}
