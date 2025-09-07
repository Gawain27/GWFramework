package com.gwngames.core.event.queue;

import com.badlogic.gdx.utils.Timer;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.event.IEventLogger;
import com.gwngames.core.api.event.IMasterEventQueue;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.event.base.AbstractEvent;

@Init(module = ModuleNames.CORE)
public class EventStatusLogger extends BaseComponent implements IEventLogger {
    @Inject
    private IMasterEventQueue masterQueue;
    private final FileLogger logger;

    public EventStatusLogger(float intervalSec, String logFilePath) {
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
