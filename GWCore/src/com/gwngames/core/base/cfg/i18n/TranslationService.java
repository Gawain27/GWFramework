package com.gwngames.core.base.cfg.i18n;

import com.gwngames.core.api.base.ITranslationService;
import com.gwngames.core.api.build.Init;
import com.gwngames.core.base.BaseComponent;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;
import com.gwngames.core.data.ModuleNames;
import com.gwngames.core.util.StringUtils;

import java.util.Locale;
import java.util.Map;

@Init(module = ModuleNames.CORE)
public final class TranslationService extends BaseComponent implements ITranslationService {
    private static final FileLogger log = FileLogger.get(LogFiles.CONFIG);
    private static volatile Map<Locale, Map<String,String>> translations = CSVTranslationLoader.load();
    private static Locale defaultLocale = Locale.US;

    public TranslationService() {}

    public String tr(String key, Locale locale) {
        if (key == null) return "";
        String value = null;
        Map<String,String> byLocale = translations.get(locale);
        if (byLocale != null) value = byLocale.get(key);
        if (StringUtils.isEmpty(value)) { // fallback to default
            Map<String,String> def = translations.get(defaultLocale);
            if (def != null) value = def.get(key);
        }
        if (StringUtils.isEmpty(value))
            throw new IllegalStateException("No valid text found: " + key  + " - locale: " + locale.toString());
        log.debug("Lookup: {} - {} -> {}", key, locale, value);
        return value;
    }

    /** Hot‑reload support, e.g. JRebel or dev mode */
    public void reload() {
        translations = CSVTranslationLoader.load();
    }

    public static void setDefaultLocale(Locale locale){
        defaultLocale = locale;
    }
}
