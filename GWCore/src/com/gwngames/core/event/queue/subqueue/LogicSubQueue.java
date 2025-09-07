package com.gwngames.core.event.queue.subqueue;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.logic.ILogicEvent;
import com.gwngames.core.api.ex.EventException;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.data.SubComponentNames;
import com.gwngames.core.event.queue.ConcurrentSubQueue;

@Init(module = ModuleNames.CORE, subComp = SubComponentNames.LOGIC_QUEUE)
public class LogicSubQueue extends ConcurrentSubQueue<ILogicEvent>{
    public LogicSubQueue(int maxParallel) {
        super(maxParallel);
    }
    @Override
    protected void processEvent(ILogicEvent ev) throws EventException {
        // TODO
    }
}
