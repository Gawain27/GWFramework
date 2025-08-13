package com.gwngames.core.input;

import com.gwngames.core.api.event.input.IInputEvent;
import com.gwngames.core.api.input.IInputAdapter;
import com.gwngames.core.api.input.IInputListener;
import com.gwngames.core.base.BaseComponent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class BaseInputAdapter extends BaseComponent implements IInputAdapter {
    private final List<IInputListener> listeners = new CopyOnWriteArrayList<>();
    private final String name;
    private int slot = -1;

    protected BaseInputAdapter(String name) {
        this.name = name;
    }

    @Override public void addListener(IInputListener l)    { listeners.add(l); }
    @Override public void removeListener(IInputListener l) { listeners.remove(l); }

    @Override public String getAdapterName() { return name; }
    @Override public int    getSlot()        { return slot; }
    @Override public void   setSlot(int slot) { this.slot = slot; }

    /** Subclasses call this to push an event out to all listeners. */
    protected void dispatch(IInputEvent evt) {
        for (IInputListener l : listeners) {
            l.onInput(evt);
        }
    }
}
