package com.gwngames.core.event.queue;

import com.gwngames.core.CoreModule;
import com.gwngames.core.CoreSubComponent;
import com.gwngames.core.api.base.cfg.IConfig;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.api.build.PostInject;
import com.gwngames.core.api.event.IEventLogger;
import com.gwngames.core.api.event.IMasterEventQueue;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;
import com.gwngames.core.data.event.EventParameters;
import com.gwngames.core.event.base.AbstractEvent;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

@Init(module = CoreModule.CORE, subComp = CoreSubComponent.EVENT_STATUS_LOGGER)
public class EventStatusLogger extends BaseComponent implements IEventLogger {
    private static final FileLogger logger = FileLogger.get(LogFiles.EVENT_STATUS);

    @Inject
    private IMasterEventQueue masterQueue;
    @Inject
    private IConfig config;

    private ScheduledExecutorService scheduler;

    @PostInject
    void init() {
        float intervalSecF = config.get(EventParameters.STATUS_LOG_SECONDS_PER_LOG);
        long intervalMs = Math.max(1L, (long) (intervalSecF * 1000f));

        ThreadFactory tf = r -> {
            Thread t = new Thread(r, "EventStatusLogger");
            t.setDaemon(true);
            return t;
        };

        scheduler = Executors.newSingleThreadScheduledExecutor(tf);
        scheduler.scheduleAtFixedRate(this::safeLog, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    private void safeLog() {
        try {
            log();
        } catch (Throwable t) {
            // Important: without this, scheduleAtFixedRate can stop executing after an exception
            logger.error("EventStatusLogger crashed: %s", t.toString());
        }
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

    public void shutdown() {
        if (scheduler != null) scheduler.shutdownNow();
    }
}
