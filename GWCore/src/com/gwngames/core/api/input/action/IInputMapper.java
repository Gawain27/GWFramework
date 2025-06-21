package com.gwngames.core.api.input.action;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.IInputEvent;
import com.gwngames.core.api.input.IInputAdapter;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.api.input.IInputListener;

@Init(forceDefinition = true)
public interface IInputMapper extends IInputListener {

    /* ---------- adapter binding ---------- */

    /** Attach this mapper to an input adapter (may be {@code null} to detach). */
    void setAdapter(IInputAdapter adapter);

    /** Current adapter or {@code null}. */
    IInputAdapter getAdapter();

    /* ---------- context handling ---------- */

    /** Active mapping set. */
    String getContext();

    /** Switch to another mapping set (creates it lazily if absent). */
    void switchContext(String context);

    /* ---------- mapping helpers ---------- */

    void map    (String context, IInputIdentifier id, IInputAction action);
    void unmap  (String context, IInputIdentifier id);
    void clear  (String context);

    /* ---------- events (from IInputListener) ---------- */

    @Override
    void onInput(IInputEvent event);
}
