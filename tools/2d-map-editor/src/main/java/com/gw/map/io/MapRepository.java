package com.gw.map.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.gw.map.model.MapDef;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists MapDef JSON files under ./maps.
 * Atomic save: write temp then move.
 */
public class MapRepository {
    private final Path mapsDir;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public MapRepository() {
        this.mapsDir =
            Paths.get(".").toAbsolutePath().normalize().resolve("maps");
    }

    public List<Path> listJsonFiles() {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(mapsDir)) return out;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(mapsDir,
            "*.json")) {
            for (Path p : ds) out.add(p);
        } catch (IOException ignored) {
        }
        return out;
    }

    public MapDef load(Path file) {
        try (Reader r = Files.newBufferedReader(file)) {
            return
                gson.fromJson(r, MapDef.class);
        } catch (IOException e) {
            throw new RuntimeException("Load failed: " +
                file, e);
        }
    }

    public void save(MapDef def) {
        if (def == null || def.id == null || def.id.isBlank()) throw new
            IllegalArgumentException("Map id required");
        try {
            Files.createDirectories(mapsDir);
            Path file = mapsDir.resolve(def.id + ".json");
            Path tmp = Files.createTempFile(mapsDir, def.id, ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp)) {
                gson.toJson(def,
                    w);
            }
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new RuntimeException("Save failed: "
                + def.id, e);
        }
    }

    public void delete(String mapId) {
        Path file = mapsDir.resolve(mapId + ".json");
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw
                new RuntimeException(e);
        }
    }
}
