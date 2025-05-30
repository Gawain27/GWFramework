package com.gwngames.core.base.cfg.i18n;

import com.opencsv.CSVReader;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class CSVTranslationLoader {

    private static final String CSV_PATH = "/translation/messages.csv";

    public static Map<Locale, Map<String,String>> load() {
        try (CSVReader reader = new CSVReader(new InputStreamReader(
            Objects.requireNonNull(CSVTranslationLoader.class.getResourceAsStream(CSV_PATH)),
            StandardCharsets.UTF_8)))
        {
            List<String[]> rows = reader.readAll();
            if (rows.isEmpty()) return Collections.emptyMap();

            // first row = locale headers (index 0 = "key")
            String[] header = rows.getFirst();
            Map<Integer, Locale> colLocales = new HashMap<>();
            for (int col=1; col<header.length; col++) {
                colLocales.put(col, Locale.forLanguageTag(header[col].replace('_','-')));
            }

            Map<Locale, Map<String,String>> bundle = new HashMap<>();
            for (String[] row : rows.subList(1, rows.size())) {
                if (row.length == 0) continue;
                String key = row[0].trim();
                for (int col=1; col<row.length; col++) {
                    Locale loc = colLocales.get(col);
                    if (loc==null) continue;
                    bundle.computeIfAbsent(loc, l -> new HashMap<>())
                        .put(key, row[col]);
                }
            }
            return bundle;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load translations CSV", e);
        }
    }
}
