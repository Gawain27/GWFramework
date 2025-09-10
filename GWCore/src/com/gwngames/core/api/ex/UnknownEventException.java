package com.gwngames.core.api.ex;

import com.gwngames.core.api.event.IEvent;
import com.gwngames.core.base.cfg.i18n.CoreTranslation;

public class UnknownEventException extends EventException {

    public UnknownEventException(IEvent event) {
        super(CoreTranslation.UNKNOWN_EVENT_ERROR, event.getClass().getSimpleName());
    }
}
