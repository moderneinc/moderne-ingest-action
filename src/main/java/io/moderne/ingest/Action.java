package io.moderne.ingest;

import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import io.moderne.ingest.parse.GradleProjectParser;
import io.moderne.ingest.parse.MavenProjectParser;
import io.moderne.ingest.service.IngestService;
import io.moderne.ingest.store.GcsSourceFileRepository;
import io.moderne.ingest.store.GcsStyleRepository;

import java.nio.file.Paths;

public class Action {

    public static void main(String[] args) {
        Storage storage = StorageOptions.getDefaultInstance().getService();
        IngestService ingestService = new IngestService(new MavenProjectParser(), new GradleProjectParser(), new GcsSourceFileRepository(storage), new GcsStyleRepository(storage));
        switch (args.length) {
            case 1:
                ingestService.ingest(Paths.get(args[0]), null);
            case 2:
                ingestService.ingest(Paths.get(args[0]), args[1]);
            default:
                throw new IllegalArgumentException("project path argument is required");
        }

    }

}
