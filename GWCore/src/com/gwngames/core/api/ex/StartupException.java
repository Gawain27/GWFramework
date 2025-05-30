package com.gwngames.core.api.ex;

public class StartupException extends BaseException {
    public StartupException(String checkClass) {
        super(ExceptionCode.STARTUP, checkClass);
    }


}
