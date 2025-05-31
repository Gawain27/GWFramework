package com.gwngames.core.base.cfg.i18n;

import com.gwngames.core.base.cfg.ModuleClassLoader;
import com.gwngames.core.base.log.FileLogger;
import com.gwngames.core.data.LogFiles;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class CSVTranslationLoader {
    private static final FileLogger log = FileLogger.get(LogFiles.CONFIG);
    private static final String CSV_PATH = "translation/messages.csv";

    public static Map<Locale, Map<String, String>> load() {
        Map<Locale, Map<String, String>> bundle = new HashMap<>();

        for (ModuleClassLoader.ProjectLoader pl : ModuleClassLoader.getInstance().getClassLoaders()) {
            try (URLClassLoader subLoader = pl.cl()){
                try (InputStream is = subLoader.getResourceAsStream(CSV_PATH)) {
                    if (is == null) continue;

                    try (CSVReader reader = new CSVReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                        List<String[]> rows = reader.readAll();
                        if (rows.isEmpty()) continue;

                        String[] header = rows.getFirst();
                        Map<Integer, Locale> colLocales = new HashMap<>();
                        for (int col = 1; col < header.length; col++) {
                            colLocales.put(col, Locale.forLanguageTag(header[col].replace('_', '-')));
                        }

                        for (String[] row : rows.subList(1, rows.size())) {
                            if (row.length == 0 || row[0].isBlank()) continue;
                            String key = row[0].trim();
                            for (int col = 1; col < row.length; col++) {
                                Locale loc = colLocales.get(col);
                                if (loc == null) continue;
                                String val = row[col];
                                if (val == null || val.isBlank()) continue;
                                bundle.computeIfAbsent(loc, l -> new HashMap<>()).putIfAbsent(key, val);
                            }
                        }
                    }
                } catch (CsvException e) {
                    log.error("Failed to load translations from module CL: {}", subLoader.getName());
                    throw new RuntimeException(e);
                }
            } catch (IOException e) {
                log.error("General failure while loading translations");
                throw new RuntimeException(e);
            }
        }
        log.debug("Translations Loaded: {}", bundle);
        return bundle;
    }
}
