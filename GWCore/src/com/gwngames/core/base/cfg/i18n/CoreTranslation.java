package com.gwngames.core.base.cfg.i18n;

import com.gwngames.core.api.build.ITranslatable;
import com.gwngames.core.api.build.Translations;

/**
 * Here we put all the translations of the core level
 * */
@Translations
public enum CoreTranslation implements ITranslatable {
	BIN_NOT_FOUND("Cannot locate bin directory (looked in $1)"),
	CONFIG_NOT_FOUND("Cannot read config.json"),
	EVENT_ERROR("Could not execute event: $1"),
	EXE_NOT_FOUND("Cannot determine executable location"),
	INCONSISTENT_ERROR("System was found in an inconsistent state: $1"),
	JAR_NOT_FOUND("Bad jar URL: $1"),
	PROJECTS_NOT_FOUND("No projects defined in config.json"),
	STARTUP_ERROR("A system check has failed: $1. Reason: $2"),
	UNKNOWN_EVENT_ERROR("Unknown event: $1");
    final String defaultCaption;
    final String key;
    CoreTranslation(String defaultCaption){
        this.defaultCaption = defaultCaption;
        this.key = String.join(".", this.name().split("_"));
    }

    @Override
    public String getKey(){
        return key;
    }
    @Override
    public String getDefaultCaption(){
        return defaultCaption;
    }
}
