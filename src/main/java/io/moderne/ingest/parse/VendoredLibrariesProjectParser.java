package io.moderne.ingest.parse;

import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.marker.JavaProvenance;
import org.openrewrite.marker.GitProvenance;
import org.openrewrite.properties.PropertiesParser;
import org.openrewrite.xml.XmlParser;
import org.openrewrite.yaml.YamlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.openrewrite.Tree.randomId;

/**
 * For when dependencies are checked in to source control.
 */
@Component
public class VendoredLibrariesProjectParser implements ProjectParser {

    private final Logger log = LoggerFactory.getLogger(VendoredLibrariesProjectParser.class);

    @Override
    public ParseResult parse(Path projectDir) {
        GitProvenance gitProvenance = GitProvenance.fromProjectDirectory(projectDir);

        ExecutionContext ctx = new InMemoryExecutionContext(t -> log.error("Error parsing", t));

        List<SourceFile> sourceFiles = new ArrayList<>();

        try {
            String javaRuntimeVersion = System.getProperty("java.runtime.version");
            String javaVendor = System.getProperty("java.vm.vendor");

            JavaProvenance.JavaVersion javaVersion = new JavaProvenance.JavaVersion(
                    javaRuntimeVersion,
                    javaVendor,
                    javaRuntimeVersion,
                    javaRuntimeVersion
            );

            JavaProvenance javaProvenance = new JavaProvenance(
                    randomId(),
                    null,
                    "main",
                    null,
                    javaVersion,
                    null
            );

            Collection<Path> classpath = Files.find(projectDir.resolve("lib"), 999, this::filterJars).collect(toList());

            JavaParser javaParser = JavaParser.fromJavaVersion()
                    .logCompilationWarningsAndErrors(true)
                    .relaxedClassTypeMatching(true)
                    .classpath(classpath)
                    .build();

            sourceFiles.addAll(ListUtils.map(javaParser.parse(Files.find(projectDir, 999, this::filterJava).collect(toList()),
                    projectDir, ctx), s -> s.withMarkers(s.getMarkers().addIfAbsent(javaProvenance))));
            sourceFiles.addAll(new XmlParser().parse(Files.find(projectDir, 999, this::filterXml).collect(toList()),
                    projectDir, ctx));
            sourceFiles.addAll(new YamlParser().parse(Files.find(projectDir, 999, this::filterYaml).collect(toList()),
                    projectDir, ctx));
            sourceFiles.addAll(new PropertiesParser().parse(Files.find(projectDir, 999, this::filterProperties).collect(toList()),
                    projectDir, ctx));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return new ParseResult(gitProvenance, ListUtils.map(sourceFiles, s -> s.withMarkers(s.getMarkers().addIfAbsent(gitProvenance))));
    }

    private boolean filterJars(Path p, BasicFileAttributes bfa) {
        return bfa.isRegularFile() && p.getFileName().toString().endsWith(".jar");
    }

    private boolean filterJava(Path p, BasicFileAttributes bfa) {
        return bfa.isRegularFile() && p.getFileName().toString().endsWith(".java");
    }

    private boolean filterXml(Path p, BasicFileAttributes bfa) {
        return bfa.isRegularFile() && p.getFileName().toString().endsWith(".xml");
    }

    private boolean filterYaml(Path p, BasicFileAttributes bfa) {
        return bfa.isRegularFile() && p.getFileName().toString().endsWith(".yaml");
    }

    private boolean filterProperties(Path p, BasicFileAttributes bfa) {
        return bfa.isRegularFile() && p.getFileName().toString().endsWith(".properties");
    }
}
