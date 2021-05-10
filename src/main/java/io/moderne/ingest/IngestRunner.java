package io.moderne.ingest;

import io.moderne.ingest.service.IngestService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Component
public class IngestRunner implements ApplicationRunner {

    private final Environment environment;
    private final IngestService ingestService;

    public IngestRunner(Environment environment, IngestService ingestService) {
        this.environment = environment;
        this.ingestService = ingestService;
    }

    @Override
    public void run(ApplicationArguments args) {
        Path projectDir;
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            projectDir = Paths.get(System.getenv("GITHUB_WORKSPACE"), "target");
        } else {
            if (args.getNonOptionArgs().isEmpty()) {
                usage();
                System.exit(-1);
            }
            projectDir = Paths.get(args.getNonOptionArgs().get(0));
        }

        if (!(Files.exists(projectDir) && Files.isDirectory(projectDir))) {
            throw new IllegalArgumentException("project directory [" + projectDir + "] does not exist or is not a directory");
        }

        if (args.containsOption("styleName")) {
            ingestService.ingest(projectDir, args.getOptionValues("styleName").get(0));
        } else {
            ingestService.ingest(projectDir, null);
        }
    }

    private void usage() {
        System.out.println("usage: IngestRunner [options] project-directory");
        System.out.println("  options:");
        System.out.println("    --styleName  Specify an available style name from app.moderne.io to apply to the generated AST");
    }
}
