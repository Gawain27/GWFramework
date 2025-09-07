package com.gwngames.core.event.queue;

import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.event.IEventQueue;
import com.gwngames.core.api.event.IMasterEventQueue;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.data.event.EventStatus;
import com.gwngames.core.api.event.IEvent;
import com.gwngames.core.api.ex.EventException;

import java.util.Deque;
import java.util.concurrent.*;

public abstract class ConcurrentSubQueue<T extends IEvent>
    extends BaseComponent implements IEventQueue {
    private final ExecutorService executor;
    private final Deque<T>        eventQueue = new ConcurrentLinkedDeque<>();

    @Inject
    protected IMasterEventQueue master;

    public ConcurrentSubQueue(int maxParallel) {
        this.executor = Executors.newFixedThreadPool(maxParallel);
    }

    /* ─────────────────── smart enqueue (front vs back) ────────────────── */
    @SuppressWarnings("unchecked")
    public void enqueue(IEvent ev) {
        if (master.canExecute(ev))
            eventQueue.offerFirst((T) ev);   // ready → front
        else
            eventQueue.offerLast((T) ev);    // waiting → back
    }

    /* ───────────────── run everything that is now eligible ────────────── */
    public void processAllEligible() {
        for (T ev : eventQueue) {
            if (ev.getStatus() == EventStatus.WAITING && master.canExecute(ev)) {
                eventQueue.remove(ev);
                ev.setStatus(EventStatus.EXECUTING);

                executor.submit(() -> {
                    try {
                        processEvent(ev);
                        ev.setStatus(EventStatus.COMPLETED);
                        master.markExecuted(ev);
                    } catch (EventException ee) {
                        ev.setStatus(EventStatus.COMPLETED);
                        master.handleEventException(ev, ee);
                    } catch (Exception ex) {
                        master.getLogger().error("Unexpected error: " + ex.getMessage(), ex);
                    }
                });
            }
        }
    }

    protected abstract void processEvent(T ev) throws EventException;

    public void shutdown() { executor.shutdown(); }
}
