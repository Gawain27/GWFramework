package com.gwngames.core.api.build;

/**
 * Annotation used to denote enum classes than contain
 * the translation key and the default caption.<p>
 * Every module may define as many as they want, gradle
 * task will check if added translation key is valid/duplicate
 * */
public @interface Translations {
}
