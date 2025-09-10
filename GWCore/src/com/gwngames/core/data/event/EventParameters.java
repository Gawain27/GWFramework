package com.gwngames.core.data.event;

import com.gwngames.core.api.cfg.IParam;
import com.gwngames.core.api.cfg.ParamKey;

public final class EventParameters {
    private EventParameters() {}

    public static final IParam<Integer> INPUT_EVENT_MAX_THREAD =
        ParamKey.of("event.input.max_thread", Integer.class);
    public static final IParam<Integer> RENDER_EVENT_MAX_THREAD =
        ParamKey.of("event.render.max_thread", Integer.class);
    public static final IParam<Integer> COMM_EVENT_MAX_THREAD =
        ParamKey.of("event.comm.max_thread", Integer.class);
    public static final IParam<Integer> SYSTEM_EVENT_MAX_THREAD =
        ParamKey.of("event.system.max_thread", Integer.class);
    public static final IParam<Integer> LOGIC_EVENT_MAX_THREAD =
        ParamKey.of("event.logic.max_thread", Integer.class);

    public static final IParam<Float> STATUS_LOG_SECONDS_PER_LOG =
        ParamKey.of("event.status.log_seconds", Float.class);
}
