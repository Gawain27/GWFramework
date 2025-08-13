package com.gwngames.core.input.action;

import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.event.input.IInputEvent;
import com.gwngames.core.api.input.*;
import com.gwngames.core.api.input.action.*;
import com.gwngames.core.api.input.buffer.*;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.input.InputContext;
import com.gwngames.core.data.LogFiles;
import com.gwngames.core.event.input.*;

import java.util.*;

/**
 * High-level mapper: converts low-level {@link IInputEvent}s to combos,
 * chains and finally {@link IInputAction}s.  It now honours
 * {@link IInputIdentifier#isRecordWhilePressed()}.
 */
public abstract class BaseInputMapper extends BaseComponent
    implements IInputMapper, IInputListener {

    /* ── collaborators supplied by sub-class ───────────────────────── */
    @Inject protected IInputComboManager comboMgr;
    @Inject protected IInputChainManager chainMgr;
    @Inject protected IInputBuffer       buffer;

    /* ── logging & statistics ──────────────────────────────────────── */
    protected static final FileLogger log = FileLogger.get(LogFiles.INPUT);
    @Inject protected IInputHistory   history;
    public IInputHistory history(){ return history; }

    /* ── adapter binding ───────────────────────────────────────────── */
    private IInputAdapter adapter;
    @Override public synchronized void setAdapter(IInputAdapter a){
        if (adapter == a) return;
        if (adapter != null) adapter.removeListener(this);
        adapter = a;
        if (adapter != null) adapter.addListener(this);
        log.debug("[mapper] adapter → {}", adapter==null? "null" : adapter.getAdapterName());
    }
    @Override public IInputAdapter getAdapter(){ return adapter; }

    /* ── context / visibility ──────────────────────────────────────── */
    private volatile InputContext context = InputContext.GAMEPLAY;
    @Override public InputContext getContext()                { return context; }
    @Override public void switchContext(InputContext ctx){
        context = Objects.requireNonNull(ctx);
        log.debug("[mapper] context → {}", ctx);
    }

    /* ── chain → action map (filled by sub-classes) ────────────────── */
    private final Map<IInputChain, EnumMap<InputContext,IInputAction>> chainMap =
        new HashMap<>();
    protected void map(IInputChain chain, Set<InputContext> vis, IInputAction action){
        EnumMap<InputContext,IInputAction> m =
            chainMap.computeIfAbsent(chain, c -> new EnumMap<>(InputContext.class));
        if (vis.isEmpty()) vis = EnumSet.allOf(InputContext.class);
        for (InputContext v : vis) m.put(v, action);
        log.debug("[mapper] mapped {} for {} vis={}", chain.name(), action, vis);
    }

    /* ── per-frame state ───────────────────────────────────────────── */
    private final Set<IInputIdentifier> held             = new HashSet<>();
    private final Set<IInputIdentifier> pressedThisFrame = new HashSet<>();
    private long frame = 0;

    /* ═════════════════════════════════ IInputListener ═══════════════ */
    @Override public void onInput(IInputEvent evt){

        IInputIdentifier id = switch (evt){
            case ButtonEvent be -> be.getControl();
            case AxisEvent   ae -> ae.getControl();
            case TouchEvent  te -> te.getControl();
            default              -> null;
        };
        if (id == null) return;

        if (evt instanceof ButtonEvent be){                    // buttons
            if (be.isPressed()){
                boolean first = held.add(id);
                if (first){
                    pressedThisFrame.add(id);
                    history.record(id);                        // always log first press
                }
            }else{
                held.remove(id);                               // released
            }
        }else{                                                 // axis / touch
            pressedThisFrame.add(id);
            history.record(id);
        }
        log.debug("[f{}] id={} held={} frameAdd={}", frame, id, held, pressedThisFrame);
    }

    /* ═════════════════════════════════ endFrame() ═══════════════════ */
    public void endFrame(){

        /* resolve combos pressed *this* frame ----------------------- */
        if (!pressedThisFrame.isEmpty()){
            List<IInputCombo> combos = comboMgr.resolve(Set.copyOf(pressedThisFrame));
            if (!combos.isEmpty()){
                combos.forEach(history::record);
                buffer.nextFrame(frame, combos);
                log.debug("[f{}] +combos {}  buf={}", frame, combos, buffer);
            }
            pressedThisFrame.clear();
        }

        /* if oldest combo’s TTL expired → try chain match ---------- */
        buffer.peekOldest().ifPresent(old -> {
            if (frame - old.frame() < old.combo().activeFrames()) return;

            log.debug("[f{}] oldest '{}' expired → evaluate chains", frame, old.combo().name());

            chainMgr.match(buffer.combos(), context).ifPresent(chain -> {
                IInputAction act = Optional.ofNullable(chainMap.get(chain))
                    .map(m -> m.get(context))
                    .orElse(null);

                if (act != null){
                    act.execute(null);                          // TODO: queue instead of direct call
                    log.debug("[f{}] chain {} ⇒ {}", frame, chain.name(), act.getClass().getSimpleName());
                }else{
                    log.debug("[f{}] chain {} matched but no action for {}", frame, chain.name(), context);
                }
                history.record(chain);
                buffer.discard(chain.combos().size());
            });
        });

        /* every-frame recording for “continuous” identifiers ------- */
        for (IInputIdentifier id : held){
            if (id.isRecordWhilePressed()){
                history.record(id);
            }
        }

        frame++;
    }
}
