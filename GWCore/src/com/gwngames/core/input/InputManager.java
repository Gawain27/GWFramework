package com.gwngames.core.input;

import com.badlogic.gdx.math.Vector2;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.base.cfg.IClassLoader;
import com.gwngames.core.api.event.IEventQueue;
import com.gwngames.core.api.event.input.*;
import com.gwngames.core.api.input.IInputAdapter;
import com.gwngames.core.api.input.IInputIdentifier;
import com.gwngames.core.api.input.IInputManager;
import com.gwngames.core.api.input.action.IInputAction;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;
import com.gwngames.core.data.input.InputType;

/**
 * Default input-event factory/dispatcher using ONLY IClassLoader.
 */
@Init(component = ComponentNames.INPUT_MANAGER, module = ModuleNames.CORE)
public final class InputManager extends BaseComponent implements IInputManager {

    @Inject(subComp = SubComponentNames.INPUT_QUEUE)
    private IEventQueue inputQueue;

    @Inject
    private IClassLoader loader;

    private static long now() { return System.nanoTime(); }

    /* ---------- CREATE ---------- */

    @Override
    public IButtonEvent createButton(IInputAdapter adapter, IInputIdentifier id, boolean pressed, float pressure) {
        // Resolve concrete via interface meta
        Init meta = IClassLoader.resolvedInit(IButtonEvent.class);
        IButtonEvent e = loader.tryCreate(meta.component()); // no-arg
        // Stamp & fill via setters
        e.setType(pressed ? InputType.BUTTON_DOWN : InputType.BUTTON_UP);
        e.setSlot(adapter.getSlot());
        e.setTimestamp(now());
        e.setControl(id);
        e.setAdapter(adapter);
        e.setPressed(pressed);
        e.setPressure(pressure);
        return e;
    }

    @Override
    public IAxisEvent createAxis(IInputAdapter adapter, IInputIdentifier id, float rawValue, float normalizedValue) {
        Init meta = IClassLoader.resolvedInit(IAxisEvent.class);
        IAxisEvent e = loader.tryCreate(meta.component());
            e.setType(InputType.AXIS_MOVE);
            e.setSlot(adapter.getSlot());
            e.setAdapter(adapter);
            e.setTimestamp(now());
            e.setRawValue(rawValue);
            e.setControl(id);
            e.setNormalizedValue(normalizedValue);
        return e;
    }

    @Override
    public ITouchEvent createTouchDown(IInputAdapter adapter, IInputIdentifier id, Vector2 pos, float pressure) {
        ITouchEvent c = buildTouch(InputType.TOUCH_DOWN, adapter.getSlot(), id, pos, pressure);
        c.setAdapter(adapter);
        return c;
    }
    @Override
    public ITouchEvent createTouchDrag(IInputAdapter adapter, IInputIdentifier id, Vector2 pos, float pressure) {
        ITouchEvent c = buildTouch(InputType.TOUCH_DRAG, adapter.getSlot(), id, pos, pressure);
        c.setAdapter(adapter);
        return c;
    }
    @Override
    public ITouchEvent createTouchUp(IInputAdapter adapter, IInputIdentifier id, Vector2 pos, float pressure) {
        ITouchEvent c = buildTouch(InputType.TOUCH_UP, adapter.getSlot(), id, pos, pressure);
        c.setAdapter(adapter);
        return c;
    }

    private ITouchEvent buildTouch(InputType type, int slot, IInputIdentifier id, Vector2 pos, float pressure) {
        var meta = IClassLoader.resolvedInit(ITouchEvent.class);
        ITouchEvent e = loader.tryCreate(meta.component()); // no-arg
        e.setType(type);
        e.setSlot(slot);
        e.setTimestamp(now());
        e.setControl(id);
        e.setPosition(new Vector2(pos)); // copy to avoid aliasing
        e.setPressure(pressure);
        return e;
    }

    /* ---------- EMIT ---------- */

    @Override public void emitButtonDown(IInputAdapter adapter, IInputIdentifier id, float pressure) {
        emit(createButton(adapter, id, true, pressure));
    }
    @Override public void emitButtonUp(IInputAdapter adapter, IInputIdentifier id, float pressure) {
        emit(createButton(adapter, id, false, pressure));
    }
    @Override public void emitAxis(IInputAdapter adapter, IInputIdentifier id, float rawValue, float normalizedValue) {
        emit(createAxis(adapter, id, rawValue, normalizedValue));
    }
    @Override public void emitTouchDown(IInputAdapter adapter, IInputIdentifier id, Vector2 pos, float pressure) {
        emit(createTouchDown(adapter, id, pos, pressure));
    }
    @Override public void emitTouchDrag(IInputAdapter adapter, IInputIdentifier id, Vector2 pos, float pressure) {
        emit(createTouchDrag(adapter, id, pos, pressure));
    }
    @Override public void emitTouchUp(IInputAdapter adapter, IInputIdentifier id, Vector2 pos, float pressure) {
        emit(createTouchUp(adapter, id, pos, pressure));
    }

    @Override
    public IInputActionEvent createActionEvent(IInputAdapter adapter, IInputAction action, IInputIdentifier control) {
        // Resolve concrete via INPUT_EVENT + INPUT_ACTION_EVENT sub-component
        IInputActionEvent e = loader.tryCreate(ComponentNames.INPUT_EVENT, SubComponentNames.INPUT_ACTION_EVENT);
        e.setType(InputType.ACTION);
        e.setSlot(adapter.getSlot());
        e.setTimestamp(now());
        e.setAdapter(adapter);
        e.setAction(action);
        // also mirror into the generic assignment to be compatible with InputSubQueue
        e.setAction(action);
        if (control != null) e.setControl(control);
        return e;
    }

    @Override
    public void emitAction(IInputAdapter adapter, IInputAction action, IInputIdentifier control) {
        emit(createActionEvent(adapter, action, control));
    }

    @Override
    public void emit(IInputEvent event) {
        if (inputQueue == null) {
            logError("[InputManager] INPUT_QUEUE not available; event dropped: {}", event);
            return;
        }
        inputQueue.enqueue(event);
    }
}
