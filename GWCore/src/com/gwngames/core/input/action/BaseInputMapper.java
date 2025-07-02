package com.gwngames.core.input.action;

import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.event.IInputEvent;
import com.gwngames.core.api.input.*;
import com.gwngames.core.api.input.action.IInputAction;
import com.gwngames.core.api.input.action.IInputHistory;
import com.gwngames.core.api.input.action.IInputMapper;
import com.gwngames.core.api.input.buffer.*;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;
import com.gwngames.core.event.input.AxisEvent;
import com.gwngames.core.event.input.ButtonEvent;
import com.gwngames.core.event.input.TouchEvent;

import java.util.*;

/**
 * <p>
 * High-level input mapper that converts a stream of low-level {@link IInputEvent}s
 * into combos → chains → actions with full visibility / context support.
 * </p>
 *
 * <h3>Algorithm overview</h3>
 * <ol>
 *   <li>{@code onInput()}
 *        <ul>
 *          <li>Keeps a set of <strong>held</strong> identifiers (keys/buttons that
 *              remain down across frames).</li>
 *          <li>Keeps a set of <strong>pressedThisFrame</strong> – every identifier
 *              that was <em>pressed at least once</em> during the current frame,
 *              even if released before the frame ends.</li>
 *          <li>Updates {@link IInputHistory} counters.</li>
 *        </ul>
 *   </li>
 *   <li>{@code endFrame()}
 *        <ul>
 *          <li>Resolves a list of {@link IInputCombo}s from
 *              {@code pressedThisFrame} via {@link IInputComboManager} and feeds
 *              them into the {@link IInputBuffer}.</li>
 *          <li>When the oldest combo’s TTL expires the buffer asks
 *              {@link IInputChainManager} for a matching chain; if found,
 *              the mapper looks up an {@link IInputAction} for the current
 *              {@link InputContext} and executes it.</li>
 *        </ul>
 *   </li>
 * </ol>
 *
 * <p>This class is <em>abstract</em>; concrete mappers are expected to:</p>
 * <ul>
 *   <li>Provide concrete instances for {@code comboMgr}, {@code chainMgr},
 *       {@code buffer} and {@code history} (usually by constructor or field
 *       injection).</li>
 *   <li>Populate the chain → action table through the protected
 *       {@link #map(IInputChain, Set, IInputAction)} helper.</li>
 *   <li>Optionally override {@code onInput()} to add direct
 *       identifier → action shortcuts that bypass the combo/chain pipeline
 *       (remember to call {@code super.onInput(evt)} to keep statistics!).</li>
 * </ul>
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
    public IInputHistory history() { return history; }

    /* ── adapter binding ───────────────────────────────────────────── */
    private IInputAdapter adapter;

    @Override
    public synchronized void setAdapter(IInputAdapter a) {
        if (adapter == a) return;
        if (adapter != null) adapter.removeListener(this);
        adapter = a;
        if (adapter != null) adapter.addListener(this);
        log.debug("[mapper] adapter → {}", adapter == null ? "null" : adapter.getAdapterName());
    }
    @Override public IInputAdapter getAdapter() { return adapter; }

    /* ── context / visibility ──────────────────────────────────────── */
    private volatile InputContext context = InputContext.GAMEPLAY;
    @Override public InputContext getContext() { return context; }
    @Override public void switchContext(InputContext ctx) {
        context = Objects.requireNonNull(ctx);
        log.debug("[mapper] context → {}", ctx);
    }

    /* ── chain → action map ────────────────────────────────────────── */
    private final Map<IInputChain, EnumMap<InputContext, IInputAction>> chainMap =
        new HashMap<>();

    /**
     * Register / overwrite the action that should fire when {@code chain}
     * matches in one of the given visibility contexts.
     */
    protected void map(IInputChain chain,
                       Set<InputContext> vis,
                       IInputAction action) {

        EnumMap<InputContext, IInputAction> m =
            chainMap.computeIfAbsent(chain, c -> new EnumMap<>(InputContext.class));

        if (vis.isEmpty()) vis = EnumSet.allOf(InputContext.class);
        for (InputContext v : vis) m.put(v, action);

        log.debug("[mapper] mapped {} for {} vis={}", chain.name(), action, vis);
    }

    /* ── per-frame state ───────────────────────────────────────────── */
    /** Keys currently held down (state survives across frames).        */
    private final Set<IInputIdentifier> held = new HashSet<>();
    /** Keys that were pressed at least once during the current frame.  */
    private final Set<IInputIdentifier> pressedThisFrame = new HashSet<>();

    private long frame = 0;

    /* ╔════════════════ InputListener ═══════════════╗ */
    @Override
    public void onInput(IInputEvent evt) {

        IInputIdentifier id = switch (evt) {
            case ButtonEvent be -> be.getControl();
            case AxisEvent   ae -> ae.getControl();
            case TouchEvent  te -> te.getControl();
            default              -> null;
        };
        if (id == null) return;

        /* ── buttons ─────────────────────────────────────────────── */
        if (evt instanceof ButtonEvent be) {

            if (be.isPressed()) {
                /* record only on the very FIRST press (transition !) */
                boolean firstPress = held.add(id);   // true if it wasn’t held before
                if (firstPress) {
                    pressedThisFrame.add(id);
                    history.record(id);
                }
            } else {                                // released
                held.remove(id);
            }
            return;
        }
        /* ── axes / touch (momentary) ────────────────────────────── */
        pressedThisFrame.add(id);
        history.record(id);

        log.debug("[f{}] id={} held={} frameAdd={}", frame, id, held, pressedThisFrame);
    }

    /* ╔══════════════════ endFrame() ════════════════╗ */
    public void endFrame() {

        /* ① combos from identifiers pressed this frame ---------------- */
        if (!pressedThisFrame.isEmpty()) {
            List<IInputCombo> combos = comboMgr.resolve(Set.copyOf(pressedThisFrame));

            if (!combos.isEmpty()) {
                combos.forEach(history::record);
                buffer.nextFrame(frame, combos);
                log.debug("[f{}] +combos {}  buf={}", frame, combos, buffer);
            }
            pressedThisFrame.clear();
        }

        /* ② try chain matching when the oldest combo has expired ------- */
        buffer.peekOldest().ifPresent(old -> {
            if (frame - old.frame() < old.combo().activeFrames()) return;

            log.debug("[f{}] oldest '{}' expired → evaluate chains", frame, old.combo().name());

            chainMgr.match(buffer.combos(), context).ifPresent(chain -> {

                IInputAction act = Optional.ofNullable(chainMap.get(chain))
                    .map(m -> m.get(context))
                    .orElse(null);

                if (act != null) {
                    act.execute(null);                               // TODO queue
                    log.debug("[f{}] chain {} ⇒ {}", frame, chain.name(), act);
                } else {
                    log.debug("[f{}] chain {} matched, no action for {}", frame, chain.name(), context);
                }
                history.record(chain);
                buffer.discard(chain.combos().size());
            });
        });

        frame++;
    }
}
