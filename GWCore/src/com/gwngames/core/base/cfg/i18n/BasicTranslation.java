package com.gwngames.core.base.cfg.i18n;

import com.gwngames.core.api.build.Translations;

@Translations
public enum BasicTranslation {
	ERROR("Error"),
	WARNING("Warning");
    final String defaultCaption;
    final String key;
    BasicTranslation(String defaultCaption){
        this.defaultCaption = defaultCaption;
        this.key = String.join(".", this.name().split("_"));
    }

    public String getKey(){
        return key;
    }
    public String getDefaultCaption(){
        return defaultCaption;
    }
}
