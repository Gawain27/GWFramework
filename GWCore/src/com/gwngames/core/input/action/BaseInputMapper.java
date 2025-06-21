package com.gwngames.core.input.action;

import com.gwngames.core.api.event.IInputEvent;
import com.gwngames.core.api.input.*;
import com.gwngames.core.api.input.action.IInputAction;
import com.gwngames.core.api.input.action.IInputMapper;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Convenience implementation of {@link IInputMapper}.
 * <ul>
 *   <li>Holds multiple context maps → {@code identifier ➜ action}</li>
 *   <li>Auto-registers as listener on the current {@link IInputAdapter}</li>
 *   <li>Logs unmapped input via {@link FileLogger}</li>
 * </ul>
 */
public abstract class BaseInputMapper extends BaseComponent implements IInputMapper {

    protected static final FileLogger log = FileLogger.get(LogFiles.INPUT);

    private final Map<String, Map<IInputIdentifier, IInputAction>> contexts =
        new ConcurrentHashMap<>();

    private volatile String context = "default"; // TODO: to enum
    private volatile IInputAdapter adapter;

    /* --------------------------------------------------------------------- */
    /*  Adapter binding                                                      */
    /* --------------------------------------------------------------------- */

    @Override
    public synchronized void setAdapter(IInputAdapter newAdapter) {
        if (adapter == newAdapter) return;
        if (adapter != null) adapter.removeListener(this);
        adapter = newAdapter;
        if (adapter != null) adapter.addListener(this);
    }

    @Override public IInputAdapter getAdapter() { return adapter; }

    /* --------------------------------------------------------------------- */
    /*  Contexts                                                             */
    /* --------------------------------------------------------------------- */

    @Override public String getContext() { return context; }

    @Override
    public void switchContext(String ctx) {
        Objects.requireNonNull(ctx, "context");
        context = ctx;
        contexts.computeIfAbsent(ctx, k -> new ConcurrentHashMap<>());
        log.debug("Switched input-mapping context to '{}'", ctx);
    }

    /* --------------------------------------------------------------------- */
    /*  Mapping helpers                                                      */
    /* --------------------------------------------------------------------- */

    @Override
    public void map(String ctx, IInputIdentifier id, IInputAction action) {
        contexts.computeIfAbsent(ctx, k -> new ConcurrentHashMap<>())
            .put(id, action);
    }

    @Override
    public void unmap(String ctx, IInputIdentifier id) {
        Map<IInputIdentifier,IInputAction> map = contexts.get(ctx);
        if (map != null) map.remove(id);
    }

    @Override
    public void clear(String ctx) {
        Map<IInputIdentifier,IInputAction> map = contexts.get(ctx);
        if (map != null) map.clear();
    }

    /* --------------------------------------------------------------------- */
    /*  Event dispatch (IInputListener)                                      */
    /* --------------------------------------------------------------------- */

    @Override
    public void onInput(IInputEvent evt) {
        Map<IInputIdentifier,IInputAction> map = contexts.get(context);
        if (map == null) return;                       // no mappings in this ctx

        IInputAction action = map.get(evt instanceof com.gwngames.core.event.input.ButtonEvent be
            ? be.getControl()
            : evt instanceof com.gwngames.core.event.input.AxisEvent ae
            ? ae.getControl()
            : evt instanceof com.gwngames.core.event.input.TouchEvent te
            ? te.getControl()
            : null);

        if (action != null) {
            try {
                action.execute(evt);
            } catch (Exception ex) {
                log.error("Action threw exception", ex);
            }
        } else {
            log.debug("Unmapped input ({}) in context '{}'", evt.getClass().getSimpleName(), context);
        }
    }
}
