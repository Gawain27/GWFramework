package com.gwngames.core.event.queue.subqueue;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.input.IInputEvent;
import com.gwngames.core.api.ex.EventException;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;
import com.gwngames.core.event.queue.ConcurrentSubQueue;

@Init(module = ModuleNames.CORE, subComp = SubComponentNames.INPUT_QUEUE)
public class InputSubQueue extends ConcurrentSubQueue<IInputEvent> {
    public InputSubQueue(int maxParallel) {
        super(maxParallel);
    }
    @Override
    protected void processEvent(IInputEvent ev) throws EventException {
        // TODO
    }
}
