package com.gwngames.core.input;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.api.input.IInputTelemetry;
import com.gwngames.core.api.input.action.IInputHistory;
import com.gwngames.core.api.input.buffer.IInputChain;
import com.gwngames.core.api.input.buffer.IInputCombo;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ModuleNames;

@Init(module = ModuleNames.CORE)
public class CoreInputTelemetry extends BaseComponent implements IInputTelemetry {
    @Inject protected IInputHistory history;
    @Override public void pressed(IInputIdentifier id, long frame) { if (history != null) history.record(id); }
    @Override public void held   (IInputIdentifier id, long frame) { if (history != null) history.record(id); }
    @Override public void combo  (IInputCombo combo, long frame)   { if (history != null) history.record(combo); }
    @Override public void chain  (IInputChain chain, long frame)   { if (history != null) history.record(chain); }
}
