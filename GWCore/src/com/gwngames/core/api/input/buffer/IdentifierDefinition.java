package com.gwngames.core.api.input.buffer;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.controllers.Controller;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.input.controls.ControllerAxisInputIdentifier;
import com.gwngames.core.input.controls.ControllerButtonInputIdentifier;
import com.gwngames.core.input.controls.KeyInputIdentifier;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Canonical list of logical game-controls used by combos & chains.
 * <p>
 * Each enum constant owns **one or more** concrete {@link IInputIdentifier}s
 * (keyboard key, controller button, axis direction …) that should be treated
 * as the <em>same</em> action in game logic.
 */
public enum IdentifierDefinition {

    /* ─────────────────────  movement  ───────────────────── */
    UP    (new KeyInputIdentifier(Keys.W)),
    DOWN  (new KeyInputIdentifier(Keys.S)),
    LEFT  (new KeyInputIdentifier(Keys.A)),

    /** Keyboard <kbd>D</kbd> **and** “Left-Stick → right” on any controller */
    RIGHT (
        new KeyInputIdentifier(Keys.D),
        // Controller axis: 0 = LS-X;  +1.0 means “fully right”.
        // Controller is set to null here – you’ll supply the *actual*
        // instance once it is discovered at runtime (see note below).
        new ControllerAxisInputIdentifier((Controller) null, 0)
    ),

    /* ────────────────────  face buttons  ─────────────────── */
    A (new KeyInputIdentifier(Keys.SPACE),                      // jump / confirm
        new ControllerButtonInputIdentifier(null, /*XBox*/0)),   // “A” on Xbox pad

    B (new KeyInputIdentifier(Keys.SHIFT_LEFT),
        new ControllerButtonInputIdentifier(null, /*XBox*/1));   // “B”

    /* ------------------------------------------------------- */

    private final Set<IInputIdentifier> ids = new HashSet<>();

    IdentifierDefinition(IInputIdentifier... ids){
        Collections.addAll(this.ids, ids);
    }

    /** Immutable view of all physical identifiers bound to this logical name. */
    public Set<IInputIdentifier> ids(){
        return Set.copyOf(ids);
    }

    /** Quick check – *does this logical control include that identifier?* */
    public boolean contains(IInputIdentifier id){
        return ids.contains(id);
    }
}
