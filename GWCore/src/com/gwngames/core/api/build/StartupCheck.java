package com.gwngames.core.api.build;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Flag to denote if a class is a check for startup.<p>
 * We can use this to create specific checks that will be automatically
 * read on startup without extra logic, so we may add requirements based on the game
 * or logic involved.<p>
 * Use the 'active' flag to disable a startup check.
 *
 * @author samlam
 * */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface StartupCheck {
}
