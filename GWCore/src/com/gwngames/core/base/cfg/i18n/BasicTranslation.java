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

    String getKey(){
        return key;
    }
    String getDefaultCaption(){
        return defaultCaption;
    }
}
