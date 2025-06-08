package com.gwngames.core.api.ex;

import com.gwngames.core.api.build.ITranslatable;
import com.gwngames.core.base.cfg.i18n.CoreTranslation;

public class StartupException extends BaseException {
    public StartupException(String checkClass, ITranslatable checkError) {
        super(CoreTranslation.STARTUP_ERROR, ExceptionCode.STARTUP, new String[2]);
        this.params[0] = checkClass;
        this.params[1] = getTranslatedText(checkError);
    }


}
