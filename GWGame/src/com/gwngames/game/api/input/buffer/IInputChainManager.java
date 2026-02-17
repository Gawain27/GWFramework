package com.gwngames.game.api.input.buffer;

import com.gwngames.DefaultModule;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.game.data.input.InputContext;
import com.gwngames.game.GameComponent;
import com.gwngames.game.api.input.action.IInputAction;

import java.util.List;
import java.util.Optional;

/** Matches a list of combos against registered chains. */
@Init(module = DefaultModule.INTERFACE, component = GameComponent.INPUT_CHAIN_MANAGER)
public interface IInputChainManager extends IBaseComp {
    void register(IInputChain c, boolean enabled);

    void enable(String n);

    void disable(String n);

    /** Try the entire buffer; return first matching chain / action or empty. */
    Optional<IInputChain> match(List<? extends IInputCombo> buffer, InputContext ctx);

    record Entry(IInputChain chain, IInputAction action) {}
}
