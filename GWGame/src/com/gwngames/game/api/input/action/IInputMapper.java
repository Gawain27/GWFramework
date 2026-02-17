package com.gwngames.game.api.input.action;

import com.gwngames.DefaultModule;
import com.gwngames.core.api.build.Init;
import com.gwngames.game.data.input.InputContext;
import com.gwngames.game.GameComponent;
import com.gwngames.game.api.event.input.IInputEvent;
import com.gwngames.game.api.input.IInputAdapter;
import com.gwngames.game.api.input.IInputIdentifier;
import com.gwngames.game.api.input.IInputListener;

@Init(component = GameComponent.INPUT_MAPPER, forceDefinition = true, module = DefaultModule.INTERFACE)
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

    void endFrame();
}
