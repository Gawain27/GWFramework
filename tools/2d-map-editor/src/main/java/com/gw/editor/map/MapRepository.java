package com.gw.editor.map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Collectors;

public class MapRepository {
    private static final Path ROOT = Path.of("editor-data", "maps");
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public MapRepository() { try { Files.createDirectories(ROOT); } catch (Exception ignored) {} }

    public List<Path> listJsonFiles() {
        try (var s = Files.list(ROOT)) {
            return s.filter(p -> p.toString().endsWith(".json")).sorted().collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void save(MapDef map) {
        if (map.id == null || map.id.isBlank()) throw new IllegalArgumentException("map.id empty");
        Path f = ROOT.resolve(map.id + ".json");
        try (Writer w = Files.newBufferedWriter(f)) {
            gson.toJson(map, w);
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    public MapDef load(Path file) {
        try (Reader r = Files.newBufferedReader(file)) {
            return gson.fromJson(r, MapDef.class);
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    public void delete(String id) {
        try { Files.deleteIfExists(ROOT.resolve(id + ".json")); } catch (IOException ignored) {}
    }
}
