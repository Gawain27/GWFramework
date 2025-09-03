package com.gwngames.core.api.input.buffer;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

/** Decides whether the coordinator should attempt chain matching this frame. */
@Init(component = ComponentNames.EVAL_TRIGGER_POLICY, module = ModuleNames.INTERFACE, allowMultiple = true, forceDefinition = true)
public interface IEvaluationTriggerPolicy extends IBaseComp {
    boolean shouldEvaluate(IInputBuffer buffer, long frame);
}
