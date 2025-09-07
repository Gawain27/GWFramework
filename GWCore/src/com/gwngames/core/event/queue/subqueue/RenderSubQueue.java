package com.gwngames.core.event.queue.subqueue;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.render.IRenderEvent;
import com.gwngames.core.api.ex.EventException;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;
import com.gwngames.core.event.queue.ConcurrentSubQueue;

@Init(module = ModuleNames.CORE, subComp = SubComponentNames.RENDER_QUEUE)
public class RenderSubQueue extends ConcurrentSubQueue<IRenderEvent> {
    public RenderSubQueue(int maxParallel) {
        super(maxParallel);
    }
    @Override
    protected void processEvent(IRenderEvent ev) throws EventException {
        // TODO
    }
}
