package com.gwngames.core.build.check;

import com.gwngames.core.api.base.check.IStartupCheck;
import com.gwngames.core.api.ex.BaseException;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;

/**
 * Note: A check component should not be per-platform, it should make
 * all the adequate checks for the operating system inside!
 * */
public abstract class StartupCheckImpl extends BaseComponent implements IStartupCheck {
    FileLogger log = FileLogger.get(LogFiles.SYSTEM);
    /** By using the required flag, you may skip or force the success of a check<p> Default true. */
    public abstract boolean isCheckRequired();

    /** Tells if the check should raise an exception, or let the executeCheck function end. */
    public abstract boolean canCheckRaiseException();

    public boolean execute() throws BaseException {
        if(isCheckRequired()){
            try {
                log.info("Ran check: {}", this.getClass().getSimpleName());
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
        log.info("Skipping non-required check: {}", this.getClass().getSimpleName());
        return true;
    }

    /** Do not use directly, go through execute() instead. */
    protected abstract boolean executeCheck() throws BaseException;
}
