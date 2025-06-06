package com.gwngames.core.event;

import com.gwngames.core.api.build.ITranslatable;
import com.gwngames.core.api.ex.EventException;

public final class DummyEventException extends EventException {
    public DummyEventException(ITranslatable errorKey, String... params) {
        super(errorKey, params);
    }
}
