package com.gwngames.core.api.input.action;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.input.IInputEvent;
import com.gwngames.core.api.input.IInputAdapter;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.api.input.IInputListener;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.input.InputContext;
import com.gwngames.core.data.ModuleNames;

@Init(component = ComponentNames.INPUT_MAPPER, forceDefinition = true, module = ModuleNames.INTERFACE)
public interface IInputMapper extends IInputListener {

    /* ---------- adapter binding ---------- */

    /** Attach this mapper to an input adapter (may be {@code null} to detach). */
    void setAdapter(IInputAdapter adapter);

    /** Current adapter or {@code null}. */
    IInputAdapter getAdapter();

    /* ---------- context handling ---------- */

    /**
     * Active mapping set.
     */
    InputContext getContext();

    /* ---------- mapping helpers ---------- */

    void map    (String context, IInputIdentifier id, IInputAction action);
    void unmap  (String context, IInputIdentifier id);
    void clear  (String context);

    /* ---------- events (from IInputListener) ---------- */

    void switchContext(InputContext c);

    @Override
    void onInput(IInputEvent event);
}
