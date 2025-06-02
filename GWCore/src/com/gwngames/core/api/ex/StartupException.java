package com.gwngames.core.api.ex;

import com.gwngames.core.base.cfg.i18n.CoreTranslation;

public class StartupException extends BaseException {
    public StartupException(String checkClass) {
        super(CoreTranslation.STARTUP_ERROR, ExceptionCode.STARTUP, checkClass);
    }


}
