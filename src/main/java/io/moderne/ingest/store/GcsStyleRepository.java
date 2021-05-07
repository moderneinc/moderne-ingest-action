package io.moderne.ingest.store;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.channels.Channels;

@Profile("prod")
@Component
public class GcsStyleRepository implements StyleRepository {

    private static final String BUCKET_NAME = "moderne-styles";

    private final Storage storage;

    public GcsStyleRepository(Storage storage) {
        this.storage = storage;
    }

    @Override
    public InputStream getStyleInputStream(String styleName) {
        Blob blob = storage.get(BUCKET_NAME, styleName + ".yml");
        if (blob == null) {
            throw new IllegalArgumentException(styleName + " not found");
        }
        return Channels.newInputStream(blob.reader());
    }
}
