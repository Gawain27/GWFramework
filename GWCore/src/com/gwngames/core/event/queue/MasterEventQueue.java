package com.gwngames.core.event.queue;

import com.gwngames.core.CoreModule;
import com.gwngames.core.CoreSubComponent;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.build.PostInject;
import com.gwngames.core.api.event.*;
import com.gwngames.core.api.event.trigger.IEventTrigger;
import com.gwngames.core.api.ex.EventException;
import com.gwngames.core.api.ex.UnknownEventException;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;
import com.gwngames.core.event.base.AbstractEvent;
import com.gwngames.core.event.base.MacroEvent;
import com.gwngames.core.event.cond.base.ConditionPolicy;
import com.gwngames.core.event.cond.base.ConditionResult;
import com.gwngames.core.event.cond.base.GlobalRule;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

@Init(module = CoreModule.CORE)
public class MasterEventQueue extends BaseComponent implements IMasterEventQueue {
    private static final FileLogger log = FileLogger.get(LogFiles.EVENT);

    // auto starts the logging process
    @Inject(subComp = CoreSubComponent.EVENT_STATUS_LOGGER)
    private IEventLogger eventStatusLogger;
    @Inject(loadAll = true)
    private List<IEventQueue> concreteSubQueues;
    private final Deque<IMacroEvent> macroQueue = new ArrayDeque<>();
    private final Set<IEvent> executedEvents = new LinkedHashSet<>();
    private final Set<IMacroEvent> completedMacros = new LinkedHashSet<>();
    private final Map<Class<? extends IEvent>, ConcurrentSubQueue<? extends IEvent>> subQueues = new HashMap<>();
    private final Map<String, GlobalRule> globalRules = new ConcurrentHashMap<>();
    private BiConsumer<IEvent, EventException> postExceptionAction;
    private final Map<String, IEventTrigger> triggers = new ConcurrentHashMap<>();

    @PostInject
    public void registerSubQueues() {
        for (IEventQueue q : concreteSubQueues) {
            registerQueue(q);
        }
    }

    @Override
    public void registerQueue(IEventQueue q){
        if (q instanceof ConcurrentSubQueue<? extends IEvent>) {
            Class<? extends IEvent> qClass = ((ConcurrentSubQueue<? extends IEvent>) q).getType();
            subQueues.put(qClass, (ConcurrentSubQueue<? extends IEvent>) q);
        }
    }

    @Override
    public synchronized void enqueueMacroEvent(IMacroEvent macroEvent) {
        macroQueue.addLast(macroEvent);
    }

    /* ───────────── helper: enqueue *single* event ───────────── */
    @Override
    public synchronized void enqueueEvent(IEvent ev) {
        /* wrap into a synthetic macro so the usual flow works */
        // TODO create from event manager
        MacroEvent wrap = new MacroEvent();
        wrap.setId("single-" + ev.getClass().getSimpleName() + "-" + UUID.randomUUID());
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
        return (c.policy() == ConditionPolicy.WAIT_UNTIL_TRUE && r != ConditionResult.TRUE)
            || (c.policy() == ConditionPolicy.EXECUTE_UNLESS_FALSE && r == ConditionResult.FALSE);
    }

    @Override
    public synchronized void markExecuted(IEvent event) {
        executedEvents.add(event);

        IMacroEvent macro = event.getMacroEvent();
        if (macro != null) {
            checkMacroCompletion(macro);
        }
    }

    private void checkMacroCompletion(IMacroEvent macroEvent) {
        if (macroEvent == null) return;
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
    public List<IMacroEvent> getMacroEvents() {
        return macroQueue.stream().toList();
    }

    @Override
    public void process(float delta) {
        triggers.values().forEach(t -> t.pollAndFire(delta));

        synchronized (this) {
            for (IMacroEvent macro : macroQueue) {
                for (IEvent e : macro.getEvents()) {

                    IEventQueue q = resolveQueueFor(e);
                    if (q == null) {
                        throw new IllegalStateException(
                            "No queue registered for event type " + e.getClass().getName()
                                + " (known queue tokens: " + subQueues.keySet().stream().map(Class::getName).toList() + ")"
                        );
                    }
                    q.enqueue(e);
                }
            }
            macroQueue.clear();
        }
        subQueues.values().forEach(ConcurrentSubQueue::processAllEligible);
    }

    /** Cache event-class -> queue mapping (after assignability resolution). */
    protected final Map<Class<?>, IEventQueue> routeCache = new ConcurrentHashMap<>();

    protected IEventQueue resolveQueueFor(IEvent e) {
        Class<?> ec = e.getClass();

        // cached?
        IEventQueue cached = routeCache.get(ec);
        if (cached != null) return cached;

        // exact key match?
        IEventQueue exact = subQueues.get(ec);
        if (exact != null) {
            routeCache.put(ec, exact);
            return exact;
        }

        // assignability match (interfaces / base types)
        for (Map.Entry<Class<? extends IEvent>, ConcurrentSubQueue<? extends IEvent>> en : subQueues.entrySet()) {
            Class<? extends IEvent> token = en.getKey();
            if (token.isAssignableFrom(ec)) {
                IEventQueue q = en.getValue();
                routeCache.put(ec, q);
                return q;
            }
        }

        return null;
    }

    @Override
    public FileLogger getLogger() {
        return log;
    }

    @Override
    public void handleEventException(IEvent event, EventException ex) {
        if (ex instanceof UnknownEventException) {
            log.error("Unknown event type: " + event.getClass().getName());
        } else if (event.getMacroEvent() != null) {
            log.error("EventException in event [%s] from MacroEvent [%s]: %s",
                event.getClass().getSimpleName(),
                event.getMacroEvent().getId(),
                ex.getMessage());
        }

        if (postExceptionAction != null) {
            postExceptionAction.accept(event, ex);
        }
    }

    public void setPostExceptionAction(BiConsumer<IEvent, EventException> action) {
        this.postExceptionAction = action;
    }

    /**
     * Register a framework-wide rule.
     *
     * @param id      unique identifier (pass {@code null} to auto-generate)
     * @param c       condition implementation
     * @param enabled initial state
     * @return the id that was stored (never {@code null})
     */
    public String addGlobalCondition(String id, IExecutionCondition c, boolean enabled) {
        String key = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
        globalRules.put(key, new GlobalRule(key, c, enabled));
        return key;
    }

    public void enableGlobalCondition(String id) {
        globalRules.get(id).setEnabled(true);
    }

    public void disableGlobalCondition(String id) {
        globalRules.get(id).setEnabled(false);
    }

    public void removeGlobalCondition(String id) {
        globalRules.remove(id);
    }

    /**
     * @return how many times this rule prevented execution.
     */
    public int getVetoCount(String id) {
        GlobalRule r = globalRules.get(id);
        return r == null ? 0 : r.getVetoCount().get();
    }

    @Override
    public void registerTrigger(IEventTrigger t) {
        triggers.put(t.getId(), t);
    }

    @Override
    public void removeTrigger(String id) {
        triggers.remove(id);
    }

    @Override
    public void enableTrigger(String id) {
        triggers.get(id).setEnabled(true);
    }

    @Override
    public void disableTrigger(String id) {
        triggers.get(id).setEnabled(false);
    }

}
