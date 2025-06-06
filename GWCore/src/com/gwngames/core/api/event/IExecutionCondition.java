package com.gwngames.core.api.event;


import com.gwngames.core.event.queue.MasterEventQueue;

public interface IExecutionCondition {
    boolean evaluate(IEvent event, MasterEventQueue masterQueue);
}
