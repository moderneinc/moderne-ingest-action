package io.moderne.ingest;

import io.moderne.ingest.service.IngestService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class IngestRunner implements ApplicationRunner {

    private final IngestService ingestService;

    public IngestRunner(IngestService ingestService) {
        this.ingestService = ingestService;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        Path projectDir = Paths.get(System.getenv("GITHUB_WORKSPACE"), "target");
        if (args.getNonOptionArgs().size() == 0) {
            ingestService.ingest(projectDir, null);
        } else {
            ingestService.ingest(projectDir, args.getNonOptionArgs().get(0));
        }
    }
}
