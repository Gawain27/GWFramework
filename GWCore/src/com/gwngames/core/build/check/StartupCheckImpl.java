package com.gwngames.core.build.check;

import com.gwngames.core.api.ex.BaseException;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;

public abstract class StartupCheckImpl{
    FileLogger log = FileLogger.get(LogFiles.SYSTEM);
    /** By using the required flag, you may skip or force the success of a check<p> Default true. */
    public abstract boolean isCheckRequired();

    /** Tells if the check should raise an exception, or let the executeCheck function end. */
    public abstract boolean canCheckRaiseException();

    public boolean execute() throws BaseException {
        if(isCheckRequired()){
            try {
                log.debug("Ran check: {}", this.getClass().getSimpleName());
                return executeCheck();
            } catch (BaseException e){
                if(canCheckRaiseException()){
                    throw e;
                } else {
                    log.error("Failed optional check: {}", e, this.getClass().getSimpleName());
                    return false;
                }
            }
        }
        log.debug("Skipping non-required check: {}", this.getClass().getSimpleName());
        return true;
    }

    /** Do not use directly, go through execute() instead. */
    protected abstract boolean executeCheck() throws BaseException;
}
