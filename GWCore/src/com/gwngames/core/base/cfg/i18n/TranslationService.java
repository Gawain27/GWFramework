package com.gwngames.core.base.cfg.i18n;

import java.util.Locale;
import java.util.Map;

public final class TranslationService {

    private static volatile Map<Locale, Map<String,String>> translations = CSVTranslationLoader.load();
    private static Locale defaultLocale = Locale.ENGLISH;

    private TranslationService() {}

    public static String tr(String key, Locale locale) {
        if (key == null) return "";
        String value = null;
        Map<String,String> byLocale = translations.get(locale);
        if (byLocale != null) value = byLocale.get(key);
        if (value == null) { // fallback to default
            Map<String,String> def = translations.get(defaultLocale);
            if (def != null) value = def.get(key);
        }
        return value != null ? value : key;
    }

    /** Hot‑reload support, e.g. JRebel or dev mode */
    public static void reload() {
        translations = CSVTranslationLoader.load();
    }

    public static void setDefaultLocale(Locale locale){
        defaultLocale = locale;
    }
}
