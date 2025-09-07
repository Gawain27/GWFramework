package com.gwngames.core.event.queue;

import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.event.*;
import com.gwngames.core.api.ex.EventException;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;
import com.badlogic.gdx.utils.*;
import com.gwngames.core.data.ModuleNames;
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

@Init(module = ModuleNames.CORE)
public class MasterEventQueue extends BaseComponent implements IMasterEventQueue {
    private final Queue<IMacroEvent> macroQueue = new Queue<>();
    private final ObjectSet<IEvent> executedEvents = new ObjectSet<>();
    private final ObjectSet<IMacroEvent> completedMacros = new ObjectSet<>();
    private final ObjectMap<Class<? extends IEvent>, ConcurrentSubQueue<? extends IEvent>> subQueues = new ObjectMap<>();
    private final Map<String, GlobalRule> globalRules = new ConcurrentHashMap<>();
    private final FileLogger logger = FileLogger.get(LogFiles.EVENT);
    private BiConsumer<IEvent, EventException> postExceptionAction;
    private final Map<String, IEventTrigger> triggers = new ConcurrentHashMap<>();

    public synchronized void registerSubQueue(Class<? extends IEvent> cls, ConcurrentSubQueue<? extends IEvent> queue) {
        subQueues.put(cls, queue);
    }

    @Override
    public synchronized void enqueueMacroEvent(IMacroEvent macroEvent) {
        macroQueue.addLast(macroEvent);
    }

    /* ───────────── helper: enqueue *single* event ───────────── */
    @Override
    public synchronized void enqueueEvent(IEvent ev) {
        /* wrap into a synthetic macro so the usual flow works */
        MacroEvent wrap = new MacroEvent("single-" + ev.getClass().getSimpleName()+ "-" + UUID.randomUUID());
        wrap.addEvent(ev);
        enqueueMacroEvent(wrap);
    }

    @Override
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
        //TODO: encapsulate this logic somewhere
        IConditionResult r = c.evaluate(ev, this);
        return (c.policy() == ConditionPolicy.WAIT_UNTIL_TRUE    && r != ConditionResult.TRUE)
            || (c.policy() == ConditionPolicy.EXECUTE_UNLESS_FALSE && r == ConditionResult.FALSE);
    }

    @Override
    public synchronized void markExecuted(IEvent event) {
        executedEvents.add(event);
        checkMacroCompletion(event.getMacroEvent());
    }

    private void checkMacroCompletion(IMacroEvent macroEvent) {
        for (IEvent event : macroEvent.getEvents())
            if (!executedEvents.contains(event)) return;
        completedMacros.add(macroEvent);
    }

    @Override
    public boolean hasExecuted(IEvent event) {
        return executedEvents.contains(event);
    }

    @Override
    public boolean isMacroEventCompleted(IMacroEvent macroEvent) {
        return completedMacros.contains(macroEvent);
    }

    @Override
    public Array<IMacroEvent> getMacroEvents() {
        return CollectionUtils.queueToArray(macroQueue);
    }

    public void process(float delta) {

        /*  let every trigger decide if it fires this frame */
        triggers.values().forEach(t -> t.pollAndFire(delta));

        /*  move macro-events’ inner events to their sub-queues */
        synchronized (this) {
            for (IMacroEvent macro : macroQueue)
                for (IEvent e : macro.getEvents())
                    subQueues.get(e.getClass()).enqueue(e); // pass master
            macroQueue.clear();
        }

        /* ask each sub-queue to execute what is now eligible */
        subQueues.values().forEach(ConcurrentSubQueue::processAllEligible);
    }

    @Override
    public FileLogger getLogger() {
        return logger;
    }

    @Override
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

    public void registerTrigger(IEventTrigger t) { triggers.put(t.getId(), t); }
    public void removeTrigger  (String id)      { triggers.remove(id);        }
    public void enableTrigger  (String id)      { triggers.get(id).setEnabled(true);  }
    public void disableTrigger (String id)      { triggers.get(id).setEnabled(false); }

}
