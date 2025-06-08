package com.gwngames.core.build.check;

import com.gwngames.core.api.build.ITranslatable;
import com.gwngames.core.api.build.Translations;

/**
 * Translations for startup checks
 * */
@Translations
public enum CheckTranslations implements ITranslatable {
    CONFIG_NOT_FOUND("Configuration data not found");

    private final String defaultCaption;
    CheckTranslations(String defaultCaption){
        this.defaultCaption = defaultCaption;
    }

    @Override
    public String getKey() {
        return this.name();
    }

    @Override
    public String getDefaultCaption() {
        return defaultCaption;
    }
}
