package com.gwngames.core.api.ex;

import static com.gwngames.core.api.ex.ExceptionLevel.*;

/**
 * Exception codes are bundles that categorize different exceptions<p>
 * This way, we can keep track on reported error and sort them by priority
 * and components used.
 * */
public enum ExceptionCode {
    SYSTEM_FAULT(CRITICAL, 0),
    INCONSISTENT_STATE(CRITICAL, 10),
    STARTUP(CRITICAL, 30),
    QUEUE(HIGH, 100);

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

