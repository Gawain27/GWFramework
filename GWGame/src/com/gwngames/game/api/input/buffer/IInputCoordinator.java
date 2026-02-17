package com.gwngames.game.api.input.buffer;

import com.gwngames.DefaultModule;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.game.data.input.InputContext;
import com.gwngames.game.GameComponent;
import com.gwngames.game.api.input.IInputIdentifier;

import java.util.Optional;
import java.util.Set;

/**
 * Orchestrates per-frame input pipeline:
 *   pressed → combos → buffer → (policy) → chain match
 * Returns a matched chain (if any) and leaves buffer/telemetry consistency to the impl.
 */
@Init(component = GameComponent.INPUT_COORDINATOR, module = DefaultModule.INTERFACE)
public interface IInputCoordinator extends IBaseComp {

    /**
     * Process one render frame.
     *
     * @param frame             current frame number
     * @param pressedThisFrame  identifiers observed this frame (edge for buttons, any activity for axes/touch)
     * @param ctx               current input visibility context
     * @return first matching chain, if any (after the evaluation trigger decides to match)
     */
    Optional<IInputChain> onFrame(long frame,
                                  Set<IInputIdentifier> pressedThisFrame,
                                  InputContext ctx);
}
