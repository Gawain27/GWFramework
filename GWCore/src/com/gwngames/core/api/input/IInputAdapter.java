package com.gwngames.core.api.input;

import com.badlogic.gdx.controllers.Controller;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.data.ComponentNames;
import com.gwngames.core.data.ModuleNames;

@Init(component = ComponentNames.INPUT_ADAPTER, module = ModuleNames.INTERFACE, allowMultiple = true)
public interface IInputAdapter extends IBaseComp {
    /** Begin processing / polling input. */
    void start();

    /** Stop processing input. */
    void stop();

    /** Register/unregister listeners for unified InputEvents. */
    void addListener(IInputListener listener);
    void removeListener(IInputListener listener);

    /** Metadata: adapter name (e.g. "Keyboard", "Controller#1"). */
    String getAdapterName();

    /** Each adapter occupies one slot (0…MAX_SLOTS-1). */
    int getSlot();
    void setSlot(int slot);
}
