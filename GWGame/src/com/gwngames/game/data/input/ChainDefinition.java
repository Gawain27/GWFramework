package com.gwngames.game.data.input;

import com.gwngames.core.api.build.Init;
import com.gwngames.game.GameModule;
import com.gwngames.game.api.input.buffer.IInputChain;
import com.gwngames.game.api.input.buffer.IInputChainManager;
import com.gwngames.game.api.input.buffer.IInputCombo;

import java.util.*;

/**
 * Declarative catalogue of <strong>motion / button chains</strong>.
 *
 * <p>Each enum constant supplies:</p>
 * <ul>
 *   <li>a non-empty, <em>ordered</em> list of {@link IInputCombo} objects</li>
 *   <li>a visibility set (<code>InputContext</code>s where the chain is legal)</li>
 * </ul>
 *
 * The enum <em>does not</em> know which action it will trigger; that decision
 * is made per-mapper via {@code mapper.map(chain, contexts, action)}.
 */
@Init(module = GameModule.GAME)
public enum ChainDefinition implements IInputChain {

    /* ───────── Core gameplay ───────────────────────────────────────── */
    DASH(
        List.of(
            ComboDefinition.DOWN_LEFT,
            ComboDefinition.RIGHT
        ),
        EnumSet.of(InputContext.GAMEPLAY)
    ),

    JUMP(
        List.of(ComboDefinition.A_ONLY),
        EnumSet.of(InputContext.GAMEPLAY)
    ),

    /* ───────── Menu / UI ───────────────────────────────────────────── */
    CONFIRM(
        List.of(ComboDefinition.A_ONLY),
        EnumSet.of(InputContext.MENU)
    ),

    CANCEL(
        List.of(ComboDefinition.B_ONLY),
        EnumSet.of(InputContext.MENU)
    ),

    /* ───────── System (pause can be triggered in many contexts) ───── */
    PAUSE(
        List.of(ComboDefinition.AB),                       // A + B together
        EnumSet.of(InputContext.GAMEPLAY,
            InputContext.PAUSE,
            InputContext.MENU)
    );

    /* ================================================================= */
    private final List<IInputCombo> seq;
    private final Set<InputContext> vis;

    ChainDefinition(List<IInputCombo> seq, Set<InputContext> vis){
        this.seq = List.copyOf(seq);
        this.vis = Set.copyOf(vis);
    }

    /* ====== IInputChain implementation =============================== */
    @Override public List<IInputCombo>    combos()      { return seq; }
    @Override public Set<InputContext>    visibility()  { return vis; }

    /* ====== Convenience helper for boot-time registration ============ */
    public static void registerAll(IInputChainManager mgr, boolean enabled){
        for (ChainDefinition c : values())
            mgr.register(c, enabled);
    }
}

