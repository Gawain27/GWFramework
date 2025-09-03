package com.gwngames.core.input.buffer;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.api.input.IInputTelemetry;
import com.gwngames.core.api.input.buffer.*;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;
import com.gwngames.core.data.input.InputContext;

import java.util.List;
import java.util.Optional;
import java.util.Set;

@Init(module = ModuleNames.CORE)
public class CoreInputCoordinator extends BaseComponent implements IInputCoordinator {

    @Inject
    protected IInputComboManager       comboMgr;
    @Inject
    protected IInputBuffer             buffer;
    @Inject
    protected IInputChainManager       chainMgr;
    @Inject
    protected IInputTelemetry telemetry;
    @Inject(subComp = SubComponentNames.COMBO_TTL_TRIGGER_POLICY)
    protected IEvaluationTriggerPolicy evalPolicy;

    @Override
    public Optional<IInputChain> onFrame(long frame, Set<IInputIdentifier> pressedThisFrame, InputContext ctx)
    {
        if (!pressedThisFrame.isEmpty()) {
            List<IInputCombo> combos = comboMgr.resolve(pressedThisFrame);
            if (!combos.isEmpty()) {
                for (IInputCombo c : combos) telemetry.combo(c, frame);
                buffer.nextFrame(frame, combos);
            }
        }

        if (!evalPolicy.shouldEvaluate(buffer, frame)) return Optional.empty();

        Optional<IInputChain> matched = chainMgr.match(buffer.combos(), ctx);
        matched.ifPresent(chain -> {
            telemetry.chain(chain, frame);
            buffer.discard(chain.combos().size());
        });
        return matched;
    }
}
