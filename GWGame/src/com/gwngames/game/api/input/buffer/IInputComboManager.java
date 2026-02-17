package com.gwngames.game.api.input.buffer;

import com.gwngames.DefaultModule;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.game.GameComponent;
import com.gwngames.game.api.input.IInputIdentifier;

import java.util.List;
import java.util.Set;

/**
 * Holds all known combo-specs and can resolve a pressed-set into a concrete spec.
 */
@Init(module = DefaultModule.INTERFACE, component = GameComponent.INPUT_COMBO_MANAGER)
public interface IInputComboManager extends IBaseComp {
    /**
     * Register a new legal combo.
     */
    void register(IInputCombo spec);

    /**
     * From the simultaneous identifiers return a preferred, non-overlapping
     * list of combos ordered oldest→newest (usually only one per frame).
     */
    List<IInputCombo> resolve(Set<IInputIdentifier> pressed);
}
