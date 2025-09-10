package com.gwngames.core.event.queue.subqueue;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.PostInject;
import com.gwngames.core.api.event.comm.ICommEvent;
import com.gwngames.core.api.event.render.IRenderEvent;
import com.gwngames.core.api.ex.EventException;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;
import com.gwngames.core.data.event.EventParameters;
import com.gwngames.core.event.queue.ConcurrentSubQueue;

@Init(module = ModuleNames.CORE, subComp = SubComponentNames.RENDER_QUEUE)
public class CommSubQueue extends ConcurrentSubQueue<ICommEvent> {
    @PostInject
    @Override
    protected void init(){
        this.maxParallel = config.get(EventParameters.COMM_EVENT_MAX_THREAD);
        super.init();
    }
    @Override
    protected void processEvent(ICommEvent ev) throws EventException {
        // TODO
    }

    @Override
    public Class<ICommEvent> getType() {
        return ICommEvent.class;
    }
}
