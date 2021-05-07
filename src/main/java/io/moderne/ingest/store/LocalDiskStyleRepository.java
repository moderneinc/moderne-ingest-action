package io.moderne.ingest.store;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class LocalDiskStyleRepository implements StyleRepository {

    @Override
    public InputStream getStyleInputStream(String styleName) {
        Path styleRepo = Paths.get(System.getProperty("user.home"), "rewrite-offline/styles");
        Path stylePath = styleRepo.resolve(styleName + ".yml");
        if (!Files.exists(stylePath)) {
            throw new IllegalArgumentException(styleName + " not found");
        }
        try {
            return Files.newInputStream(stylePath);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
