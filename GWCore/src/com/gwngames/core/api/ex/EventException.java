package com.gwngames.core.api.ex;

import com.gwngames.core.api.build.ITranslatable;

public abstract class EventException extends BaseException {
    public EventException(ITranslatable errorKey, String... params) {
        super(errorKey, ExceptionCode.QUEUE, params);
    }
}
