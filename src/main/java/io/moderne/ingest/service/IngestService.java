package io.moderne.ingest.service;

import io.moderne.ingest.parse.GradleProjectParser;
import io.moderne.ingest.parse.MavenProjectParser;
import io.moderne.ingest.parse.ParseResult;
import io.moderne.ingest.store.SourceFileRepository;
import io.moderne.ingest.store.StyleRepository;
import org.openrewrite.SourceFile;
import org.openrewrite.config.YamlResourceLoader;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.style.Autodetect;
import org.openrewrite.java.style.ImportLayoutStyle;
import org.openrewrite.java.style.SpacesStyle;
import org.openrewrite.java.style.TabsAndIndentsStyle;
import org.openrewrite.java.tree.J;
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
import java.util.*;
import java.util.stream.Collectors;

@Component
public class IngestService {

    private final Logger log = LoggerFactory.getLogger(IngestService.class);

    private final MavenProjectParser mavenProjectParser;
    private final GradleProjectParser gradleProjectParser;
    private final SourceFileRepository sourceFileRepository;
    private final StyleRepository styleRepository;

    public IngestService(MavenProjectParser mavenProjectParser,
                         GradleProjectParser gradleProjectParser, SourceFileRepository sourceFileRepository,
                         StyleRepository styleRepository) {
        this.mavenProjectParser = mavenProjectParser;
        this.gradleProjectParser = gradleProjectParser;
        this.sourceFileRepository = sourceFileRepository;
        this.styleRepository = styleRepository;
    }

    public void ingest(Path projectDir, @Nullable String styleName) {
        try {
            ParseResult parseResult;
            if (Files.exists(projectDir.resolve("pom.xml"))) {
                log.info("Parsing maven project at {}", projectDir);
                parseResult = mavenProjectParser.parse(projectDir);
            } else if (Files.exists(projectDir.resolve("build.gradle")) || Files.exists(projectDir.resolve("build.gradle.kts"))) {
                log.info("Parsing gradle project at {}", projectDir);
                parseResult = gradleProjectParser.parse(projectDir);
            } else {
                throw new IllegalArgumentException(projectDir + " doesn't contain build.gradle, build.gradle.kts, or pom.xml. Unable to parse project.");
            }

            Collection<NamedStyles> namedStyles;
            if (styleName != null) {
                InputStream styleInputStream = styleRepository.getStyleInputStream(styleName);
                YamlResourceLoader yamlResourceLoader;
                try {
                    yamlResourceLoader = new YamlResourceLoader(styleInputStream, new URI("urn:moderne-saas:" + styleName), new Properties());
                } catch (URISyntaxException e) {
                    throw new RuntimeException(e);
                }
                namedStyles = yamlResourceLoader.listStyles();
            } else {
                Autodetect autodetect = Autodetect.detect(parseResult.getSourceFiles().stream()
                        .filter(J.CompilationUnit.class::isInstance)
                        .map(J.CompilationUnit.class::cast)
                        .collect(Collectors.toList()));

                namedStyles = Collections.singletonList(autodetect);

                ImportLayoutStyle importLayout = NamedStyles.merge(ImportLayoutStyle.class, namedStyles);
                assert importLayout != null;
                log.info("Auto-detected import layout:");
                log.info(importLayout.toString());

                SpacesStyle spacesStyle = NamedStyles.merge(SpacesStyle.class, namedStyles);
                assert spacesStyle != null;
                log.info("Auto-detected spaces style:");
                log.info(spacesStyle.toString());

                TabsAndIndentsStyle tabsStyle = NamedStyles.merge(TabsAndIndentsStyle.class, namedStyles);
                assert tabsStyle != null;
                log.info("Auto-detected tabs and indents:");
                log.info(tabsStyle.toString());
            }

            List<SourceFile> sourceFiles = parseResult.getSourceFiles();
            for (int i = 0; i < sourceFiles.size(); i++) {
                SourceFile sourceFile = sourceFiles.get(i);
                List<Marker> markers = new ArrayList<>(sourceFile.getMarkers().entries());
                markers.addAll(namedStyles);
                sourceFiles.set(i, sourceFile.withMarkers(Markers.build(markers)));
            }

            log.info("Storing AST");
            sourceFileRepository.store(parseResult);
        } catch (Exception e) {
            log.error("Exception ingesting", e);
        }
    }
}
