package com.gw.world.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.gw.world.model.WorldDef;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists WorldDef JSON files under ./worlds or ./model/worlds/{configSet}.
 * Atomic save: write temp then move.
 */
public class WorldRepository {

    private final Path worldsDir;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public WorldRepository() {
        // Root dir of the repo, passed from Gradle; fallback to CWD
        String rootDirProp = System.getProperty("gw.rootDir");
        Path rootDir = (rootDirProp != null && !rootDirProp.isBlank())
            ? Paths.get(rootDirProp).toAbsolutePath().normalize()
            : Paths.get(".").toAbsolutePath().normalize();

        // configSet passed from Gradle (-PconfigSet=... -> JavaExec systemProperty)
        String cfg = System.getProperty("configSet");

        if (cfg != null && !cfg.isBlank()) {
            // model/worlds/{configSet}
            this.worldsDir = rootDir.resolve("model").resolve("worlds").resolve(cfg);
        } else {
            // fallback for other tooling / tests
            this.worldsDir = rootDir.resolve("worlds");
        }
    }

    public List<Path> listJsonFiles() {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(worldsDir)) return out;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(worldsDir, "*.json")) {
            for (Path p : ds) out.add(p);
        } catch (IOException ignored) {
        }
        return out;
    }

    public WorldDef load(Path file) {
        try (Reader r = Files.newBufferedReader(file)) {
            return gson.fromJson(r, WorldDef.class);
        } catch (IOException e) {
            throw new RuntimeException("World load failed: " + file, e);
        }
    }

    public void save(WorldDef def) {
        if (def == null || def.id == null || def.id.isBlank()) {
            throw new IllegalArgumentException("World id required");
        }
        try {
            Files.createDirectories(worldsDir);
            Path file = worldsDir.resolve(def.id + ".json");
            Path tmp = Files.createTempFile(worldsDir, def.id, ".tmp");
            try (Writer w = Files.newBufferedWriter(tmp)) {
                gson.toJson(def, w);
            }
            Files.move(tmp, file,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new RuntimeException("World save failed: " + def.id, e);
        }
    }

    public void delete(String worldId) {
        Path file = worldsDir.resolve(worldId + ".json");
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
