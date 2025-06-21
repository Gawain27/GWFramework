package com.gwngames.core.input.action;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.*;
import com.gwngames.core.api.input.action.IInputAction;
import com.gwngames.core.api.input.action.IInputActionManager;
import com.gwngames.core.api.input.action.IInputMapper;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;
import com.gwngames.core.data.ModuleNames;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Central registry for:
 * <ol>
 *   <li>⚙️ one global instance <em>per concrete {@link IInputAction} class</em></li>
 *   <li>🎛 runtime attachment/detachment of {@link IInputMapper}s to any
 *       {@link IInputAdapter} (slot)</li>
 * </ol>
 */
@Init(module = ModuleNames.CORE)
public class InputActionManager extends BaseComponent implements IInputActionManager {

    private static final FileLogger log = FileLogger.get(LogFiles.INPUT);
    private static final InputActionManager INSTANCE = new InputActionManager();
    public  static InputActionManager get() { return INSTANCE; }

    /* ───────────────────────── 1. action singletons ──────────────────── */
    private final Map<Class<? extends IInputAction>, IInputAction> actions = new ConcurrentHashMap<>();

    /* ───────────────────────── 2. mapper ↔ adapter wiring ─────────────── */
    /** keeps current adapter per mapper (null ⇒ detached) */
    private final Map<IInputMapper, IInputAdapter> mapperBinding = new ConcurrentHashMap<>();

    private InputActionManager() { }

    /* ===========================================================
     *  ACTION SINGLETON API
     * ========================================================= */
    @SuppressWarnings("unchecked")
    @Override
    public <T extends IInputAction> T getOrCreate(Class<T> type, Supplier<T> factory) {
        return (T) actions.computeIfAbsent(type, k -> {
            T a = Objects.requireNonNull(factory.get(),
                "factory returned null for "+type.getSimpleName());
            log.debug("Created InputAction {}", type.getSimpleName());
            return a;
        });
    }
    @SuppressWarnings("unchecked")
    @Override
    public <T extends IInputAction> T getIfPresent(Class<T> type) { return (T) actions.get(type); }

    @Override
    public void register(IInputAction action) {
        Objects.requireNonNull(action);
        IInputAction prev = actions.putIfAbsent(action.getClass(), action);
        if (prev != null && prev != action)
            throw new IllegalStateException("Action already registered for "+action.getClass());
    }

    /* ===========================================================
     *  MAPPER API
     * ========================================================= */

    /** Attach mapper to adapter; detaches from previous one automatically. */
    public void attachMapper(IInputMapper mapper, IInputAdapter adapter) {
        Objects.requireNonNull(mapper,  "mapper");
        Objects.requireNonNull(adapter, "adapter");

        IInputAdapter old = mapperBinding.put(mapper, adapter);
        if (old == adapter) return;          // already bound

        if (old != null) old.removeListener(mapper);
        mapper.setAdapter(adapter);

        log.debug("Mapper {} bound to adapter {}", mapper.getClass().getSimpleName(),
            adapter.getAdapterName());
    }

    /** Detach mapper from whichever adapter it is currently listening to. */
    public void detachMapper(IInputMapper mapper) {
        IInputAdapter old = mapperBinding.remove(mapper);
        if (old != null) {
            old.removeListener(mapper);
            mapper.setAdapter(null);
            log.debug("Mapper {} detached from {}", mapper.getClass().getSimpleName(),
                old.getAdapterName());
        }
    }

    /** Convenience: detach all mappers – useful for scene switches or tests. */
    public void detachAllMappers() { mapperBinding.keySet().forEach(this::detachMapper); }

    /** Current adapter of the mapper or <code>null</code>. */
    public IInputAdapter adapterOf(IInputMapper m){ return mapperBinding.get(m); }

    /** Immutable view of all active mapper→adapter bindings. */
    public Map<IInputMapper,IInputAdapter> bindings(){
        return Collections.unmodifiableMap(mapperBinding);
    }

    /* ===========================================================
     *  TEST / maintenance
     * ========================================================= */
    public void clear() {
        actions.clear();
        detachAllMappers();
        log.debug("InputActionManager cleared");
    }
}
