package com.gwngames.game.api.input.action;

import com.gwngames.DefaultModule;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.game.GameComponent;

import java.util.function.Supplier;

@Init(module = DefaultModule.INTERFACE, component = GameComponent.INPUT_ACTION_MANAGER)
public interface IInputActionManager extends IBaseComp {
    /* ===========================================================
     *  ACTION SINGLETON API
     * ========================================================= */
    <T extends IInputAction> T getOrCreate(Class<T> type, Supplier<T> factory);

    <T extends IInputAction> T getIfPresent(Class<T> type);

    void register(IInputAction action);
}
