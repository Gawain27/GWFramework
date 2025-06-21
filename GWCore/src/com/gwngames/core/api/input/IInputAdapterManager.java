package com.gwngames.core.api.input;

import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

import java.util.List;

@Init(module = ModuleNames.INTERFACE, component = ComponentNames.INPUT_ADAPTER_MANAGER)
public interface IInputAdapterManager extends IBaseComp {
    /** Slot‐based registration (0…MAX_SLOTS-1). */
    void register(int slot, IInputAdapter adapter);
    void unregister(int slot);
    void setMainController(int slot);
    IInputAdapter getAdapter(int slot);
    IInputAdapter getMainAdapter();

    /** Listen for *physical* device plug/unplug events. */
    void addDeviceListener(IInputDeviceListener listener);
    void removeDeviceListener(IInputDeviceListener listener);

    /** All adapters your system *knows about* (discovered but not necessarily in a slot). */
    List<IInputAdapter> getAllAdapters();

    /** The subset of adapters *currently assigned* to slots. */
    List<IInputAdapter> getActiveAdapters();
}

