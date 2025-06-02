package com.gwngames.core.api.build;

/**
 * Every enum that carries a translation key+default text should implement this.
 */
public interface ITranslatable {
    /**
     * Returns the string key used to look up the translation in resource bundles,
     * e.g. “ERROR” or “warning.file_not_found”.
     */
    String getKey();

    /**
     * Returns the fallback/default caption (in English) if no translation is found.
     */
    String getDefaultCaption();
}
