package com.gwngames.core.api.ex;

import java.util.List;

public class InconsistentStateException extends BaseException{

    public InconsistentStateException(String state) {
        super(ExceptionCode.generate(InconsistentStateException.class,
            List.of(ExceptionCode.THOUSAND, ExceptionCode.DECA)), state);
    }
}
