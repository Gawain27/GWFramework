package com.gwngames.core.input;

import com.gwngames.core.api.base.cfg.IClassLoader;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.input.IInputAdapter;
import com.gwngames.core.api.input.IInputListener;
import com.gwngames.core.api.input.IInputManager;
import com.gwngames.core.base.BaseComponent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public abstract class BaseInputAdapter extends BaseComponent implements IInputAdapter {
    @Inject
    protected IInputManager inputManager;
    @Inject
    protected IClassLoader loader;

    private final List<IInputListener> listeners = new CopyOnWriteArrayList<>();
    private int slot = -1;

    @Override
    public void addListener(IInputListener l) {
        listeners.add(l);
    }
    @Override
    public void removeListener(IInputListener l) {
        listeners.remove(l);
    }

    @Override
    public int getSlot()        {
        return slot;
    }
    @Override
    public void setSlot(int slot) {
        this.slot = slot;
    }
    @Override
    public List<IInputListener> getListeners() {
        return listeners;
    }
}
