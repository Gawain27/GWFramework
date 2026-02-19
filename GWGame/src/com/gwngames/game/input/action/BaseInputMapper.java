package com.gwngames.game.input.action;

import com.gwngames.core.api.build.Inject;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.game.api.event.input.IAxisEvent;
import com.gwngames.game.api.event.input.IButtonEvent;
import com.gwngames.game.api.event.input.IInputEvent;
import com.gwngames.game.api.event.input.ITouchEvent;
import com.gwngames.game.api.input.*;
import com.gwngames.game.api.input.action.IInputAction;
import com.gwngames.game.api.input.action.IInputMapper;
import com.gwngames.game.api.input.buffer.IInputChain;
import com.gwngames.game.api.input.buffer.IInputCoordinator;
import com.gwngames.game.data.input.InputContext;
import com.gwngames.core.data.LogFiles;

import java.util.*;

/**
 * High-level mapper: converts low-level {@link IInputEvent}s to combos,
 * chains and finally {@link IInputAction}s.  It now honours
 * {@link IInputIdentifier#isRecordWhilePressed()}.
 */
public abstract class BaseInputMapper extends BaseComponent implements IInputMapper, IInputListener {
    protected static final FileLogger log = FileLogger.get(LogFiles.INPUT);
    @Inject
    protected IInputManager inputManager;
    @Inject
    protected IInputCoordinator coordinator;
    @Inject
    protected IInputTelemetry telemetry;

    /* ── adapter binding ───────────────────────────────────────────── */
    private IInputAdapter adapter;

    @Override public synchronized void setAdapter(IInputAdapter a){
        if (adapter == a) return;
        if (adapter != null) adapter.removeListener(this);
        adapter = a;
        if (adapter != null) adapter.addListener(this);
        log.debug("[mapper] adapter → {}", adapter==null? "null" : adapter.getAdapterName());
    }
    @Override
    public IInputAdapter getAdapter(){
        return adapter;
    }

    /* ── context / visibility ──────────────────────────────────────── */
    private volatile InputContext context = InputContext.GAMEPLAY;

    @Override
    public InputContext getContext()
    { return context; }
    @Override
    public void switchContext(InputContext ctx){
        context = Objects.requireNonNull(ctx);
        log.debug("[mapper] context → {}", ctx);
    }

    /* ── chain → action map (filled by sub-classes) ────────────────── */
    private final Map<IInputChain, EnumMap<InputContext,IInputAction>> chainMap =
        new HashMap<>();

    protected void map(IInputChain chain, Set<InputContext> vis, IInputAction action){
        EnumMap<InputContext, IInputAction> m =
            chainMap.computeIfAbsent(chain, c -> new EnumMap<>(InputContext.class));
        if (vis.isEmpty()) vis = EnumSet.allOf(InputContext.class);
        for (InputContext v : vis) m.put(v, action);
        log.debug("[mapper] mapped {} for {} vis={}", chain.name(), action, vis);
    }

    /* ── per-frame state ───────────────────────────────────────────── */
    private final Set<IInputIdentifier> held             = new HashSet<>();
    private final Set<IInputIdentifier> pressedThisFrame = new HashSet<>();
    private long frame = 0;

    @Override
    public String identity(){
        StringBuilder id = new StringBuilder();
        id.append(getClass().getSimpleName());
        id.append("-");
        if (adapter != null){
            id.append(adapter.getAdapterName());
        } else {
            id.append("Unknown");
        }
        id.append("-");
        id.append(getContext().name());

        return id.toString();
    }

    @Override
    public void onInput(IInputEvent evt){

        IInputIdentifier id = switch (evt){
            case IButtonEvent be -> be.getControl();
            case IAxisEvent ae -> ae.getControl();
            case ITouchEvent te -> te.getControl();
            default              -> null;
        };
        if (id == null) return;

        if (evt instanceof IButtonEvent be){                    // buttons
            if (be.isPressed()){
                boolean first = held.add(id);
                if (first){
                    pressedThisFrame.add(id);
                    telemetry.pressed(id, frame);              // first down this hold
                } else {
                    telemetry.held(id, frame);
                }
            } else {
                boolean wasHeld = held.remove(id);
                if (wasHeld) telemetry.released(id, frame);    // record release
            }
        }
        else if (evt instanceof IAxisEvent ae){
            // Analog input sample
            telemetry.axis(id, ae.getRawValue(), ae.getNormalizedValue(), frame);
            // Axis is continuous; do NOT mutate held/pressed sets.
        }
        else { // TouchEvent
            ITouchEvent te = (ITouchEvent) evt;
            float x = te.getPosition().x;
            float y = te.getPosition().y;
            float p = te.getPressure();

            switch (te.getType()) {
                case TOUCH_DOWN -> {
                    boolean first = held.add(id);
                    if (first) pressedThisFrame.add(id);
                    telemetry.touchDown(id, x, y, p, frame);
                }
                case TOUCH_DRAG -> // Keep held as-is; pointer still down
                    telemetry.touchDrag(id, x, y, p, frame);
                case TOUCH_UP -> {
                    boolean wasHeld = held.remove(id);
                    telemetry.touchUp(id, x, y, p, frame);
                    if (wasHeld) telemetry.released(id, frame); // optional parity with buttons
                }
                default -> // Fallback: treat unknown types as a drag sample
                    telemetry.touchDrag(id, x, y, p, frame);
            }
        }

        log.debug("[f{}] id={} held={} frameAdd={}", frame, id, held, pressedThisFrame);
    }

    @Override
    public void endFrame(){
        Optional<IInputChain> foundChain = coordinator.onFrame(frame, pressedThisFrame, context);
        if(foundChain.isPresent()){
            IInputChain chain = foundChain.get();
            IInputAction act = Optional.ofNullable(chainMap.get(chain))
                .map(m -> m.get(context))
                .orElse(null);

            if (act != null){
                int slot = adapter != null ? adapter.getSlot() : 0; // fallback if needed
                // control is optional here; pass null or derive one if you prefer
                inputManager.emitAction(adapter, act, null);
                log.debug("[f{}] chain {} ⇒ queued {}", frame, chain.name(), act.getClass().getSimpleName());
            }else{
                log.debug("[f{}] chain {} matched but no action for {}", frame, chain.name(), context);
            }
        }
        frame++;
    }

}
