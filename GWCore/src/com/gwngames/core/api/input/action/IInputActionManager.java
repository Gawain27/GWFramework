package com.gwngames.core.api.input.action;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

import java.util.function.Supplier;

@Init(module = ModuleNames.INTERFACE, component = ComponentNames.INPUT_ACTION_MANAGER)
public interface IInputActionManager extends IBaseComp {
    /* ===========================================================
     *  ACTION SINGLETON API
     * ========================================================= */
    <T extends IInputAction> T getOrCreate(Class<T> type, Supplier<T> factory);

    <T extends IInputAction> T getIfPresent(Class<T> type);

    void register(IInputAction action);
}
