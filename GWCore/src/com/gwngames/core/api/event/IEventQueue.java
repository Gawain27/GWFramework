package com.gwngames.core.api.event;

import com.gwngames.DefaultModule;
import com.gwngames.core.CoreComponent;
import com.gwngames.core.api.base.IBaseComp;
import com.gwngames.core.api.build.Init;

@Init(module = DefaultModule.INTERFACE, component = CoreComponent.EVENT_QUEUE, forceDefinition = true, allowMultiple = true)
public interface IEventQueue extends IBaseComp {
    /* ─────────────────── smart enqueue (front vs back) ────────────────── */
    /**
     * Allows direct processing, without sequentialization.<br>
     * Execute at any moment, not at start of frame
     * */
    void enqueue(IEvent ev);
}
