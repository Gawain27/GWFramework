package com.gwngames.core.api.build;

/**
 * Annotation used to denote enum classes than contain
 * the translation key and the default caption.<p>
 * Every module may define as many as they want, gradle
 * task will check if added translation key is valid/duplicate<p>
 * FOR GOD'S SAKE, since people probably won't keep natural ordering,
 * there is a build task that orders it automatically.
 * */
public @interface Translations {
}
