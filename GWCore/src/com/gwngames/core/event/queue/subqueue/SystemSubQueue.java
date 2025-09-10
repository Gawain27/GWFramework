package com.gwngames.core.event.queue.subqueue;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.PostInject;
import com.gwngames.core.api.event.system.ISystemEvent;
import com.gwngames.core.api.ex.EventException;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;
import com.gwngames.core.data.event.EventParameters;
import com.gwngames.core.event.queue.ConcurrentSubQueue;

@Init(module = ModuleNames.CORE, subComp = SubComponentNames.SYSTEM_QUEUE)
public class SystemSubQueue extends ConcurrentSubQueue<ISystemEvent> {
    @PostInject
    @Override
    protected void init(){
        this.maxParallel = config.get(EventParameters.SYSTEM_EVENT_MAX_THREAD);
        super.init();
    }
    @Override
    protected void processEvent(ISystemEvent ev) throws EventException {
        // TODO
    }

    @Override
    public Class<ISystemEvent> getType() {
        return ISystemEvent.class;
    }
}
