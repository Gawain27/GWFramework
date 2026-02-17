package com.gwngames.game.input.buffer;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.game.GameModule;
import com.gwngames.game.GameSubComponent;
import com.gwngames.game.api.input.buffer.IEvaluationTriggerPolicy;
import com.gwngames.game.api.input.buffer.IInputBuffer;

/** Evaluate strictly when the oldest combo's TTL expires. */
@Init(module = GameModule.GAME, subComp = GameSubComponent.COMBO_TTL_TRIGGER_POLICY)
public class TtlOnlyTriggerPolicy extends BaseComponent implements IEvaluationTriggerPolicy {

    @Override
    public boolean shouldEvaluate(IInputBuffer buffer, long frame) {
        return buffer.peekOldest().map(old ->
            frame - old.frame() >= old.combo().activeFrames()
        ).orElse(false);
    }
}
