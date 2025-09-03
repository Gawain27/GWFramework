package com.gwngames.core.input.buffer;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.input.buffer.IEvaluationTriggerPolicy;
import com.gwngames.core.api.input.buffer.IInputBuffer;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;

/** Evaluate strictly when the oldest combo's TTL expires. */
@Init(module = ModuleNames.CORE, subComp = SubComponentNames.COMBO_TTL_TRIGGER_POLICY)
public class TtlOnlyTriggerPolicy extends BaseComponent implements IEvaluationTriggerPolicy {
    @Override
    public boolean shouldEvaluate(IInputBuffer buffer, long frame) {
        return buffer.peekOldest().map(old ->
            frame - old.frame() >= old.combo().activeFrames()
        ).orElse(false);
    }
}
