package com.gwngames.core.event.queue;

import com.gwngames.core.api.event.EventStatus;
import com.gwngames.core.api.ex.EventException;
import com.gwngames.core.event.base.AbstractEvent;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class ConcurrentSubQueue<T extends AbstractEvent> {
    private final ExecutorService executorService;
    private final Queue<T> eventQueue = new ConcurrentLinkedQueue<>();

    public ConcurrentSubQueue(int maxConcurrentEvents) {
        this.executorService = Executors.newFixedThreadPool(maxConcurrentEvents);
    }

    @SuppressWarnings("unchecked")
    public void enqueue(AbstractEvent event) {
        eventQueue.add((T) event);
    }

    public void processAllEligible(MasterEventQueue masterQueue) {
        for (T event : eventQueue) {
            if (event.getStatus() == EventStatus.WAITING && masterQueue.canExecute(event)) {
                eventQueue.remove(event);
                event.setStatus(EventStatus.EXECUTING);

                executorService.submit(() -> {
                    try {
                        processEvent(event);
                        event.setStatus(EventStatus.COMPLETED);
                        masterQueue.markExecuted(event);
                    } catch (EventException ee) {
                        event.setStatus(EventStatus.COMPLETED); // Still consider it done for macro completion
                        masterQueue.handleEventException(event, ee); // Delegate to master
                    } catch (Exception ex) {
                        masterQueue.getLogger().error("Unexpected error in event processing: " + ex.getMessage(), ex);
                    }
                });
            }
        }
    }

    protected abstract void processEvent(T event) throws EventException;

    public void shutdown() {
        executorService.shutdown();
    }
}
