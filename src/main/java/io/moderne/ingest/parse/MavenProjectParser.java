package io.moderne.ingest.parse;

import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.java.JavaParser;
import org.openrewrite.marker.GitProvenance;
import org.openrewrite.maven.MavenParser;
import org.openrewrite.maven.cache.*;
import org.openrewrite.maven.internal.MavenDownloadingException;
import org.openrewrite.maven.internal.MavenParsingException;
import org.openrewrite.maven.utilities.MavenArtifactDownloader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Consumer;

@Component
public class MavenProjectParser implements ProjectParser {

    private final Logger log = LoggerFactory.getLogger(MavenProjectParser.class);

    @Override
    public ParseResult parse(Path projectDir) {
        Consumer<Throwable> errorConsumer = t -> {
            if (t instanceof MavenParsingException) {
                log.debug("Maven Parsing Exception", t);
            } else if (t instanceof MavenDownloadingException) {
                log.debug("Maven Downloading Exception", t);
            } else {
                log.error("Error parsing", t);
            }
        };

        MavenArtifactDownloader downloader = new MavenArtifactDownloader(ReadOnlyLocalMavenArtifactCache.mavenLocal().orElse(
                new LocalMavenArtifactCache(Paths.get(System.getProperty("user.home"), ".rewrite-cache", "artifacts"))
        ),
                null,
                errorConsumer);

        MavenPomCache pomCache = new RocksdbMavenPomCache(projectDir);

        MavenParser.Builder mavenParserBuilder = MavenParser.builder()
                .cache(pomCache)
                .mavenConfig(projectDir.resolve(".mvn/maven.config"));

        org.openrewrite.maven.utilities.MavenProjectParser parser = new org.openrewrite.maven.utilities.MavenProjectParser(
                downloader,
                mavenParserBuilder,
                JavaParser.fromJavaVersion()
                        .logCompilationWarningsAndErrors(true)
                        .relaxedClassTypeMatching(true),
                new InMemoryExecutionContext(errorConsumer)
        );

        List<SourceFile> sourceFiles = parser.parse(projectDir);
        GitProvenance gitProvenance = sourceFiles.stream().findFirst()
                .flatMap(s -> s.getMarkers().findFirst(GitProvenance.class))
                .orElseThrow(() -> new IllegalStateException("GitProvenance not found for " + projectDir));
        return new ParseResult(gitProvenance, sourceFiles);
    }

    public static void main(String[] args) {
        new MavenProjectParser().parse(Paths.get(args[0]));
    }
}
