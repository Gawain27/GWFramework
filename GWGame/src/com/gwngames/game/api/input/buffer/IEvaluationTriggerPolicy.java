package com.gwngames.game.api.input.buffer;

import com.gwngames.DefaultModule;
import com.gwngames.core.CoreComponent;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;

/** Decides whether the coordinator should attempt chain matching this frame. */
@Init(component = CoreComponent.EVAL_TRIGGER_POLICY, module = DefaultModule.INTERFACE, allowMultiple = true, forceDefinition = true)
public interface IEvaluationTriggerPolicy extends IBaseComp {
    boolean shouldEvaluate(IInputBuffer buffer, long frame);
}
