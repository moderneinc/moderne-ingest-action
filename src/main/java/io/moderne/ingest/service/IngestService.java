package io.moderne.ingest.service;

import io.moderne.ingest.parse.GradleProjectParser;
import io.moderne.ingest.parse.MavenProjectParser;
import io.moderne.ingest.parse.ParseResult;
import io.moderne.ingest.store.SourceFileRepository;
import io.moderne.ingest.store.StyleRepository;
import org.openrewrite.SourceFile;
import org.openrewrite.config.YamlResourceLoader;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.marker.GitProvenance;
import org.openrewrite.marker.Marker;
import org.openrewrite.marker.Markers;
import org.openrewrite.style.NamedStyles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static java.util.stream.Collectors.toList;

@Component
public class IngestService {

    private final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final MavenProjectParser mavenProjectParser;
    private final GradleProjectParser gradleProjectParser;
    private final SourceFileRepository sourceFileRepository;
    private final StyleRepository styleRepository;
    private final ExecutorService executorService;

    public IngestService(MavenProjectParser mavenProjectParser,
                         GradleProjectParser gradleProjectParser, SourceFileRepository sourceFileRepository,
                         StyleRepository styleRepository) {
        this.mavenProjectParser = mavenProjectParser;
        this.gradleProjectParser = gradleProjectParser;
        this.sourceFileRepository = sourceFileRepository;
        this.styleRepository = styleRepository;
        this.executorService = Executors.newFixedThreadPool(2);
    }

    public void ingest(Path projectDir, @Nullable String styleName) {
        Collection<NamedStyles> namedStyles = null;
        if (styleName != null) {
            InputStream styleInputStream = styleRepository.getStyleInputStream(styleName);
            YamlResourceLoader yamlResourceLoader;
            try {
                yamlResourceLoader = new YamlResourceLoader(styleInputStream, new URI("urn:moderne-saas:" + styleName), new Properties());
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
            namedStyles = yamlResourceLoader.listStyles();
        }
        final Collection<NamedStyles> finalNamedStyles = namedStyles;

        GitProvenance git = GitProvenance.fromProjectDirectory(projectDir);

        executorService.execute(() -> {

            try {
                ParseResult parseResult;
                if (Files.exists(projectDir.resolve("build.gradle")) || Files.exists(projectDir.resolve("build.gradle.kts"))) {
                    log.info("Parsing gradle project");
                    parseResult = gradleProjectParser.parse(projectDir);
                } else if (Files.exists(projectDir.resolve("pom.xml"))) {
                    log.info("Parsing maven project");
                    parseResult = mavenProjectParser.parse(projectDir);
                } else {
                    throw new IllegalArgumentException(projectDir + " doesn't contain build.gradle, build.gradle.kts, or pom.xml. Unable to parse project.");
                }
                if (finalNamedStyles != null) {
                    parseResult.setSourceFiles(parseResult.getSourceFiles().stream().map(sf -> {
                        List<Marker> markers = new ArrayList<>(sf.getMarkers().entries());
                        markers.addAll(finalNamedStyles);
                        return sf.withMarkers(Markers.build(markers));
                    }).map(SourceFile.class::cast).collect(toList()));
                }

                log.info("Storing AST");
                sourceFileRepository.store(parseResult);
            } catch (Exception e) {
                log.error("Exception ingesting", e);
            }
        });
    }
}
