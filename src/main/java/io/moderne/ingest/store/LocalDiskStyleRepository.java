package io.moderne.ingest.store;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Profile("!prod")
@Component
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
