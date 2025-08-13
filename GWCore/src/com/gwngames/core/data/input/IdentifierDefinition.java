package com.gwngames.core.data.input;

import com.badlogic.gdx.Input.Keys;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.input.controls.ControllerAxisInputIdentifier;
import com.gwngames.core.input.controls.ControllerButtonInputIdentifier;
import com.gwngames.core.input.controls.KeyInputIdentifier;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Canonical mapping of **logical** controls (“UP”, “A Button” …) to all the
 * **physical** identifiers that should trigger them (keyboard, controller,
 * touch, …).
 */
public enum IdentifierDefinition {

    /* ───────────────── movement ───────────────── */
    UP    (new KeyInputIdentifier(Keys.W, true)),
    DOWN  (new KeyInputIdentifier(Keys.S, true)),
    LEFT  (new KeyInputIdentifier(Keys.A, true)),

    /** <kbd>D</kbd> key *or* left-stick tilted right. */
    RIGHT (
        new KeyInputIdentifier(Keys.D, true),
        new ControllerAxisInputIdentifier(null, 0, true)          // LS-X (+1.0 ⇒ right)
    ),

    /* ───────────────── face buttons ───────────── */
    A (new KeyInputIdentifier(Keys.SPACE, false),           // jump / confirm
        new ControllerButtonInputIdentifier(null, 0, false)),      // “A” on Xbox pad

    B (new KeyInputIdentifier(Keys.SHIFT_LEFT, false),
        new ControllerButtonInputIdentifier(null, 1, false));      // “B”

    /* ------------------------------------------------------------------ */

    private final Set<IInputIdentifier> ids = new HashSet<>();

    IdentifierDefinition(IInputIdentifier... ids){
        Collections.addAll(this.ids, ids);
    }

    /** Immutable view of all physical identifiers of this logical control. */
    public Set<IInputIdentifier> ids(){ return Set.copyOf(ids); }

    /** Does this definition include the given physical identifier? */
    public boolean contains(IInputIdentifier id){ return ids.contains(id); }
}
