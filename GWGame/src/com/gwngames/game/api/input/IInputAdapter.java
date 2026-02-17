package com.gwngames.game.api.input;

import com.gwngames.DefaultModule;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;
import com.gwngames.game.GameComponent;

import java.util.List;

@Init(component = GameComponent.INPUT_ADAPTER, module = DefaultModule.INTERFACE, allowMultiple = true)
public interface IInputAdapter extends IBaseComp {
    /** Begin processing / polling input. */
    void start();

    /** Stop processing input. */
    void stop();

    /** Register/unregister listeners for unified InputEvents. */
    void addListener(IInputListener listener);
    void removeListener(IInputListener listener);
    List<IInputListener> getListeners();

    /** Metadata: adapter name (e.g. "Keyboard", "Controller#1"). */
    String getAdapterName();

    /** Each adapter occupies one slot (0…MAX_SLOTS-1). */
    int getSlot();
    void setSlot(int slot);
}
