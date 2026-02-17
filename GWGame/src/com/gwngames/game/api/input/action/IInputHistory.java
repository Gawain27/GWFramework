package com.gwngames.game.api.input.action;

import com.gwngames.DefaultModule;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.game.GameComponent;
import com.gwngames.game.api.input.IInputIdentifier;
import com.gwngames.game.api.input.buffer.IInputChain;
import com.gwngames.game.api.input.buffer.IInputCombo;

import java.util.Map;

@Init(component = GameComponent.INPUT_HISTORY, module = DefaultModule.INTERFACE)
public interface IInputHistory extends IBaseComp {
    void record(IInputIdentifier id);
    void release(IInputIdentifier id);
    void record(IInputCombo combo);
    void record(IInputChain chain);

    // Existing views…
    Map<IInputIdentifier, Long> identifiers();
    Map<IInputIdentifier, Long> releases();
    Map<IInputCombo, Long> combos();
    Map<IInputChain, Long> chains();

    // NEW: axis/touch hooks (default no-ops)
    default void axis(IInputIdentifier id, float raw, float normalized) {}
    default void touchDown(IInputIdentifier id, float x, float y, float pressure) {}
    default void touchDrag(IInputIdentifier id, float x, float y, float pressure) {}
    default void touchUp  (IInputIdentifier id, float x, float y, float pressure) {}

    void clear();
}
