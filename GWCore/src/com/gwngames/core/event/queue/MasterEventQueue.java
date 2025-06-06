package com.gwngames.core.event.queue;

import com.gwngames.core.api.event.IEvent;
import com.gwngames.core.api.event.IExecutionCondition;
import com.gwngames.core.api.ex.EventException;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;
import com.badlogic.gdx.utils.*;
import com.gwngames.core.event.base.AbstractEvent;
import com.gwngames.core.event.base.MacroEvent;
import com.gwngames.core.util.CollectionUtils;

import java.util.function.BiConsumer;

public class MasterEventQueue {
    private final Queue<MacroEvent> macroQueue = new Queue<>();
    private final ObjectSet<IEvent> executedEvents = new ObjectSet<>();
    private final ObjectSet<MacroEvent> completedMacros = new ObjectSet<>();
    private final Array<IExecutionCondition> conditions = new Array<>();
    private final ObjectMap<Class<? extends IEvent>, ConcurrentSubQueue<? extends AbstractEvent>> subQueues = new ObjectMap<>();
    private final FileLogger logger = FileLogger.get(LogFiles.EVENT);

    private BiConsumer<IEvent, EventException> postExceptionAction;

    public synchronized void registerSubQueue(Class<? extends IEvent> cls, ConcurrentSubQueue<? extends AbstractEvent> queue) {
        subQueues.put(cls, queue);
    }

    public synchronized void enqueueMacroEvent(MacroEvent macroEvent) {
        macroQueue.addLast(macroEvent);
    }

    public void addCondition(IExecutionCondition condition) {
        conditions.add(condition);
    }

    public boolean canExecute(IEvent event) {
        for (IExecutionCondition condition : conditions)
            if (!condition.evaluate(event, this)) return false;
        return true;
    }

    public synchronized void markExecuted(IEvent event) {
        executedEvents.add(event);
        checkMacroCompletion(event.getMacroEvent());
    }

    private void checkMacroCompletion(MacroEvent macroEvent) {
        for (IEvent event : macroEvent.getEvents())
            if (!executedEvents.contains(event)) return;
        completedMacros.add(macroEvent);
    }

    public boolean hasExecuted(IEvent event) {
        return executedEvents.contains(event);
    }

    public boolean isMacroEventCompleted(MacroEvent macroEvent) {
        return completedMacros.contains(macroEvent);
    }

    public Array<MacroEvent> getMacroEvents() {
        return CollectionUtils.queueToArray(macroQueue);
    }

    public Array<IExecutionCondition> getConditions() {
        return conditions;
    }

    public void process(float delta) {
        synchronized (this) {
            for (MacroEvent macro : macroQueue)
                for (IEvent event : macro.getEvents())
                    subQueues.get(event.getClass()).enqueue((AbstractEvent) event);
            macroQueue.clear();
        }

        subQueues.values().forEach(q -> q.processAllEligible(this));
    }

    public FileLogger getLogger() {
        return logger;
    }

    public void handleEventException(IEvent event, EventException ex) {
        logger.error("EventException in event [%s] from MacroEvent [%s]: %s",
            event.getClass().getSimpleName(),
            event.getMacroEvent().getId(),
            ex.getMessage());

        if (postExceptionAction != null) {
            postExceptionAction.accept(event, ex);
        }
    }

    public void setPostExceptionAction(BiConsumer<IEvent, EventException> action) {
        this.postExceptionAction = action;
    }
}
