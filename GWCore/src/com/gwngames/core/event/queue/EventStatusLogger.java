package com.gwngames.core.event.queue;

import com.badlogic.gdx.utils.Timer;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.event.base.AbstractEvent;

public class EventStatusLogger {
    private final MasterEventQueue masterQueue;
    private final FileLogger logger;

    public EventStatusLogger(MasterEventQueue masterQueue, float intervalSec, String logFilePath) {
        this.masterQueue = masterQueue;
        this.logger = FileLogger.get(logFilePath);
        Timer.schedule(new Timer.Task() {
            public void run() { log(); }
        }, intervalSec, intervalSec);
    }

    private void log() {
        masterQueue.getMacroEvents().forEach(macro -> {
            logger.info("MacroEvent: %s", macro.getId());
            macro.getEvents().forEach(e -> {
                AbstractEvent ae = (AbstractEvent) e;
                logger.info(" - Event: %s Status: %s Duration: %dms",
                    e.getClass().getSimpleName(), ae.getStatus(), ae.getExecutionDuration());
            });
        });
    }
}
