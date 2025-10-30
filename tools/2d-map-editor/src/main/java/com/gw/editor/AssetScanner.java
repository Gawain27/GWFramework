package com.gw.editor;

import com.gwngames.core.api.asset.IAssetManager;
import com.gwngames.core.api.build.Inject;
import com.gwngames.core.asset.BuiltInSubTypes;
import com.gwngames.core.util.Cdi;
import javafx.scene.image.Image;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Super simple asset lister: scans a folder for png/jpg/jpeg. */
public class AssetScanner {
    @Inject
    private IAssetManager manager;

    public AssetScanner() {
        Cdi.inject(this);
    }

    public List<AssetEntry> scanAll() {
        List<String> assetsPaths = manager.listAssets(BuiltInSubTypes.TEXTURE);
        List<AssetEntry> entries = new ArrayList<>();
        for (String assetPath : assetsPaths) {
            String absPath = manager.toAbsolute(assetPath);
            Path absolutePath = Path.of(absPath);
            Image image = loadThumb(absolutePath);
            AssetEntry entry = new AssetEntry(assetPath, absolutePath, image);
            entries.add(entry);
        }
        return entries;
    }

    private Image loadThumb(Path p) {
        try (FileInputStream in = new FileInputStream(p.toFile())) {
            // Let JavaFX scale later; we just load once.
            return new Image(in);
        } catch (IOException e) {
            return null;
        }
    }

    public record AssetEntry(String logicalPath, Path absolutePath, Image thumbnail) {}
}
