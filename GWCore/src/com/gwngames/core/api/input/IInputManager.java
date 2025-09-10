package com.gwngames.core.api.input;

import com.badlogic.gdx.math.Vector2;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.input.*;
import com.gwngames.core.api.input.action.IInputAction;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

/** Factory + dispatcher for framework input events (via IClassLoader only). */
@Init(component = ComponentNames.INPUT_MANAGER, module = ModuleNames.INTERFACE)
public interface IInputManager extends IBaseComp {

    /* CREATE (returns an event; caller may enqueue later) */
    IButtonEvent createButton(IInputAdapter adapter, IInputIdentifier id, boolean pressed, float pressure);
    IAxisEvent createAxis  (IInputAdapter adapter, IInputIdentifier id, float rawValue, float normalizedValue);
    ITouchEvent createTouchDown(IInputAdapter adapter, IInputIdentifier id, Vector2 pos, float pressure);
    ITouchEvent createTouchDrag(IInputAdapter adapter, IInputIdentifier id, Vector2 pos, float pressure);
    ITouchEvent createTouchUp  (IInputAdapter adapter, IInputIdentifier id, Vector2 pos, float pressure);
    /** create an ActionEvent bound to an adapter (control is optional). */
    IInputActionEvent createActionEvent(IInputAdapter adapter, IInputAction action, IInputIdentifier control);


    /* EMIT (create + enqueue into INPUT_QUEUE) */
    void emitButtonDown(IInputAdapter adapter, IInputIdentifier id, float pressure);
    void emitButtonUp  (IInputAdapter adapter, IInputIdentifier id, float pressure);
    void emitAxis      (IInputAdapter adapter, IInputIdentifier id, float rawValue, float normalizedValue);
    void emitTouchDown (IInputAdapter adapter, IInputIdentifier id, Vector2 pos, float pressure);
    void emitTouchDrag (IInputAdapter adapter, IInputIdentifier id, Vector2 pos, float pressure);
    void emitTouchUp   (IInputAdapter adapter, IInputIdentifier id, Vector2 pos, float pressure);
    /** emit an ActionEvent into the input queue. */
    void emitAction(IInputAdapter adapter, IInputAction action, IInputIdentifier control);


    /** Enqueue any pre-built input event. */
    void emit(IInputEvent event);
}
