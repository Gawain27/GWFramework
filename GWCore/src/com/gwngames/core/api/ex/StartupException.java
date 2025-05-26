package com.gwngames.core.api.ex;

import java.util.List;

public class StartupException extends BaseException {
    public StartupException(String checkClass) {
        super(ExceptionCode.STARTUP, checkClass);
    }


}
