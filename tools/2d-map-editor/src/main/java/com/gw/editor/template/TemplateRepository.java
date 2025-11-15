package com.gw.editor.template;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class TemplateRepository {
    private final Path templatesDir;
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public TemplateRepository() {
        // Root dir of the repo, passed from Gradle; fallback to CWD
        String rootDirProp = System.getProperty("gw.rootDir");
        Path rootDir = (rootDirProp != null && !rootDirProp.isBlank())
            ? Paths.get(rootDirProp).toAbsolutePath().normalize()
            : Paths.get(".").toAbsolutePath().normalize();

        // configSet passed from Gradle
        String cfg = System.getProperty("configSet");

        if (cfg != null && !cfg.isBlank()) {
            // model/templates/{configSet}
            this.templatesDir = rootDir.resolve("model").resolve("templates").resolve(cfg);
        } else {
            // fallback for other use cases
            this.templatesDir = rootDir.resolve("templates");
        }
    }

    public List<Path> listJsonFiles() {
        List<Path> out = new ArrayList<>();
        if (!Files.isDirectory(templatesDir)) return out;
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(templatesDir, "*.json")) {
            for (Path p : ds) out.add(p);
        } catch (IOException ignored) {}
        return out;
    }

    public TemplateDef load(Path file) {
        try (Reader r = Files.newBufferedReader(file)) {
            return gson.fromJson(r, TemplateDef.class);
        } catch (IOException e) {
            throw new RuntimeException("Load failed: " + file, e);
        }
    }

    public void save(TemplateDef def) {
        if (def.id == null || def.id.isBlank()) throw new IllegalArgumentException("Template id required");
        try {
            Files.createDirectories(templatesDir);
            Path file = templatesDir.resolve(def.id + ".json");
            try (Writer w = Files.newBufferedWriter(file)) {
                gson.toJson(def, w);
            }
        } catch (IOException e) {
            throw new RuntimeException("Save failed: " + def.id, e);
        }
    }

    public void delete(String templateId) {
        Path file = templatesDir.resolve(templateId + ".json");
        try { Files.deleteIfExists(file); } catch (IOException e) { throw new RuntimeException(e); }
    }

    public TemplateDef findById(String id) {
        for (Path p : listJsonFiles()) {
            TemplateDef t = load(p);
            if (t != null && id != null && id.equals(t.id)) return t;
        }
        return null;
    }

    /** Load and return all templates in the repository. */
    public List<TemplateDef> listAll() {
        List<TemplateDef> all = new ArrayList<>();
        for (Path p : listJsonFiles()) {
            try {
                all.add(load(p));
            } catch (RuntimeException ignored) { /* skip malformed */ }
        }
        return all;
    }
}
