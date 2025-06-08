package com.gwngames.core.event.queue;

import com.gwngames.core.api.event.IEvent;
import com.gwngames.core.api.event.IExecutionCondition;
import com.gwngames.core.api.ex.EventException;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;
import com.badlogic.gdx.utils.*;
import com.gwngames.core.event.base.AbstractEvent;
import com.gwngames.core.event.base.MacroEvent;
import com.gwngames.core.event.cond.base.ConditionPolicy;
import com.gwngames.core.event.cond.base.ConditionResult;
import com.gwngames.core.event.cond.base.GlobalRule;
import com.gwngames.core.util.CollectionUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

//TODO: make component for MasterEventQueue
public class MasterEventQueue {
    private final Queue<MacroEvent> macroQueue = new Queue<>();
    private final ObjectSet<IEvent> executedEvents = new ObjectSet<>();
    private final ObjectSet<MacroEvent> completedMacros = new ObjectSet<>();
    private final ObjectMap<Class<? extends IEvent>, ConcurrentSubQueue<? extends AbstractEvent>> subQueues = new ObjectMap<>();
    private final Map<String, GlobalRule> globalRules = new ConcurrentHashMap<>();
    private final FileLogger logger = FileLogger.get(LogFiles.EVENT);
    private BiConsumer<IEvent, EventException> postExceptionAction;

    public synchronized void registerSubQueue(Class<? extends IEvent> cls, ConcurrentSubQueue<? extends AbstractEvent> queue) {
        subQueues.put(cls, queue);
    }

    public synchronized void enqueueMacroEvent(MacroEvent macroEvent) {
        macroQueue.addLast(macroEvent);
    }

    public boolean canExecute(IEvent ev) {

        /* 1 – global guard rails */
        for (GlobalRule rule : globalRules.values()) {
            if (!rule.isEnabled()) continue;

            if (fails(rule.getCondition(), ev)) {
                rule.getVetoCount().incrementAndGet();
                return false;
            }
        }

        /* 2 – event-specific rules (if any) */
        if (ev instanceof AbstractEvent ae) {
            for (IExecutionCondition c : ae.getConditions())
                if (fails(c, ev)) return false;
        }
        return true;
    }

    private boolean fails(IExecutionCondition c, IEvent ev) {
        ConditionResult r = c.evaluate(ev, this);
        return (c.policy() == ConditionPolicy.WAIT_UNTIL_TRUE    && r != ConditionResult.TRUE)
            || (c.policy() == ConditionPolicy.EXECUTE_UNLESS_FALSE && r == ConditionResult.FALSE);
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

    public void process(float delta) {
        synchronized (this) {
            for (MacroEvent macro : macroQueue)
                for (IEvent event : macro.getEvents())
                    subQueues.get(event.getClass()).enqueue((AbstractEvent) event);
            macroQueue.clear();
        }

        subQueues.values().forEach(q -> q.processAllEligible());
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

    /**
     * Register a framework-wide rule.
     * @param id       unique identifier (pass {@code null} to auto-generate)
     * @param c        condition implementation
     * @param enabled  initial state
     * @return the id that was stored (never {@code null})
     */
    public String addGlobalCondition(String id, IExecutionCondition c, boolean enabled) {
        String key = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
        globalRules.put(key, new GlobalRule(key, c, enabled));
        return key;
    }

    public void enableGlobalCondition (String id) { globalRules.get(id).setEnabled(true); }
    public void disableGlobalCondition(String id) { globalRules.get(id).setEnabled(false); }
    public void removeGlobalCondition (String id) { globalRules.remove(id); }

    /** @return how many times this rule prevented execution. */
    public int getVetoCount(String id) {
        GlobalRule r = globalRules.get(id);
        return r == null ? 0 : r.getVetoCount().get();
    }
}
