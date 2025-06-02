package com.gwngames.core.api.ex;

import com.gwngames.core.base.cfg.i18n.CoreTranslation;

import java.util.List;

public class InconsistentStateException extends BaseException{

    public InconsistentStateException(String state) {
        super(CoreTranslation.INCONSISTENT_ERROR, ExceptionCode.INCONSISTENT_STATE, state);
    }
}
