package com.gwngames.core.api.ex;

import static com.gwngames.core.api.ex.ExceptionLevel.CRITICAL;

public enum ExceptionCode {
    SYSTEM_FAULT(CRITICAL, 0),
    INCONSISTENT_STATE(CRITICAL, 10),
    STARTUP(CRITICAL, 30);

    final int problemLevel;
    final int exceptionId;
    ExceptionCode(int problemLevel, int id){
        this.problemLevel = problemLevel;
        this.exceptionId = id;
    }
    public int getProblemLevel(){
        return problemLevel;
    }
    public int getExceptionId(){
        return exceptionId;
    }
}

