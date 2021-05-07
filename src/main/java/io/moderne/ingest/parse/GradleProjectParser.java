package io.moderne.ingest.parse;

import com.google.common.collect.Lists;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.internal.consumer.DefaultGradleConnector;
import org.gradle.tooling.model.GradleProject;
import org.gradle.tooling.model.Task;
import org.gradle.tooling.model.eclipse.EclipseProject;
import org.gradle.tooling.model.eclipse.EclipseProjectDependency;
import org.gradle.tooling.model.eclipse.EclipseSourceDirectory;
import org.openrewrite.ExecutionContext;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeSerializer;
import org.openrewrite.java.JavaParser;
import org.openrewrite.marker.GitProvenance;
import org.openrewrite.marker.Markers;
import org.openrewrite.properties.PropertiesParser;
import org.openrewrite.xml.XmlParser;
import org.openrewrite.yaml.YamlParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Collections.emptyList;
import static java.util.stream.Collectors.toList;

@Component
public class GradleProjectParser implements ProjectParser {
    private static final Logger log = LoggerFactory.getLogger(GradleProjectParser.class);

    private static final Set<String> potentialExcludeTasks = new HashSet<>();

    static {
        potentialExcludeTasks.add("javadoc");
        potentialExcludeTasks.add("nodeSetup");
        potentialExcludeTasks.add("npmSetup");
        potentialExcludeTasks.add("yarnSetup");
        potentialExcludeTasks.add("npmInstall");
        potentialExcludeTasks.add("yarn");
        potentialExcludeTasks.add("bundle");
    }

    @Override
    public ParseResult parse(Path projectDir) {
        Consumer<Throwable> errorConsumer = t -> log.error("Error parsing", t);
        InMemoryExecutionContext ctx = new InMemoryExecutionContext(errorConsumer);

        List<SourceFile> sourceFiles = new ArrayList<>();

        GradleConnector gradleConnector = GradleConnector.newConnector()
                .forProjectDirectory(projectDir.toFile());
        if (gradleConnector instanceof DefaultGradleConnector) {
            ((DefaultGradleConnector)gradleConnector).daemonMaxIdleTime(60, TimeUnit.SECONDS);
        }

        try (ProjectConnection connection = gradleConnector
                .connect()) {

            GradleProject gradleProject = connection.model(GradleProject.class).get();
            Set<String> taskNames = getTaskNames(gradleProject);
            taskNames.retainAll(potentialExcludeTasks);
            List<String> buildArguments = new ArrayList<>();
            buildArguments.add("-Dorg.gradle.jvmargs=\"-Xmx1g\"");
            for (String taskName : taskNames) {
                buildArguments.add("-x");
                buildArguments.add(taskName);
            }
            // compile java sources
            connection.newBuild()
                    .forTasks("assemble", "testClasses")
                    .withArguments(buildArguments)
                    .run();

            EclipseProject eclipseProject = connection.model(EclipseProject.class).get();
            parseEclipseProject(eclipseProject, eclipseProject, sourceFiles, projectDir, ctx);
        } finally {
            gradleConnector.disconnect();
        }

        GitProvenance gitProvenance = GitProvenance.fromProjectDirectory(projectDir);
        for (int i = 0; i < sourceFiles.size(); i++) {
            SourceFile sourceFile = sourceFiles.get(i);
            sourceFiles.set(i, sourceFile.withMarkers(Markers.build(Lists.newArrayList(gitProvenance))));
        }

        return new ParseResult(gitProvenance, sourceFiles);
    }

    private Set<String> getTaskNames(GradleProject project) {
        Set<String> taskNames = new HashSet<>();
        for (Task t : project.getTasks()) {
            taskNames.add(t.getName());
        }
        for (GradleProject child : project.getChildren()) {
            taskNames.addAll(getTaskNames(child));
        }
        return taskNames;
    }

    private void parseEclipseProject(EclipseProject project, EclipseProject rootProject, List<SourceFile> sourceFiles, Path projectDir, ExecutionContext ctx) {
        for (EclipseSourceDirectory sourceDirectory : project.getSourceDirectories()) {
            if (sourceDirectory.getPath().endsWith("/resources")) {
                File resourceDirectory = sourceDirectory.getDirectory();
                sourceFiles.addAll(new XmlParser().parse(getSources(resourceDirectory, "xml"), projectDir, ctx));
                sourceFiles.addAll(new YamlParser().parse(getSources(resourceDirectory, "yml"), projectDir, ctx));
                sourceFiles.addAll(new PropertiesParser().parse(getSources(resourceDirectory, "properties"), projectDir, ctx));
            } else {
                JavaParser javaParser = JavaParser.fromJavaVersion()
                        .classpath(dependencies(project, rootProject))
                        .build();
                sourceFiles.addAll(javaParser.parse(getSources(sourceDirectory.getDirectory(), "java"), projectDir, ctx));
            }
        }
        for (EclipseProject subproject : project.getChildren()) {
            parseEclipseProject(subproject, rootProject, sourceFiles, projectDir, ctx);
        }
    }

    private Set<Path> dependencies(EclipseProject project, EclipseProject rootProject) {
        Set<Path> paths = new LinkedHashSet<>(getProjectBuildPaths(project));
        project.getClasspath().stream().map(cp -> cp.getFile().toPath()).forEach(paths::add);
        project.getProjectDependencies().forEach(pd -> addProjectDependencies(pd, rootProject, paths));
        return paths;
    }

    private void addProjectDependencies(EclipseProjectDependency projectDependency, EclipseProject rootProject, Set<Path> classpathAcc) {
        EclipseProject dependencyProject = rootProject.getChildren().stream()
                .filter(childProject -> childProject.getName().equals(projectDependency.getPath()))
                .findAny()
                .orElseThrow(() ->
                        new IllegalStateException("Unable to locate project " + projectDependency.getPath()));
        classpathAcc.addAll(getProjectBuildPaths(dependencyProject));
        dependencyProject.getClasspath().stream().map(cp -> cp.getFile().toPath()).forEach(classpathAcc::add);
    }

    private static final Pattern sourceDirectoryPattern = Pattern.compile("src/(.*?)/(.*?)");

    private Set<Path> getProjectBuildPaths(EclipseProject project) {
        Set<Path> buildPaths = new LinkedHashSet<>();
        for (EclipseSourceDirectory sourceDirectory : project.getSourceDirectories()) {
            Matcher m = sourceDirectoryPattern.matcher(sourceDirectory.getPath());
            if (m.matches()) {
                String sourceSet = m.group(1);
                Path classesDir = project.getProjectDirectory().toPath().resolve("build/classes");
                try {
                    Files.walk(classesDir, 3).filter(p -> p.endsWith(sourceSet)).forEach(buildPaths::add);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            } else {
                log.warn("Unrecognized source directory {}", sourceDirectory.getPath());
            }
        }
        return buildPaths;
    }

    private static List<Path> getSources(File srcDir, String... fileTypes) {
        if (!srcDir.exists()) {
            return emptyList();
        }

        BiPredicate<Path, BasicFileAttributes> predicate = (p, bfa) ->
                bfa.isRegularFile() && Arrays.stream(fileTypes).anyMatch(type -> p.getFileName().toString().endsWith(type));
        try {
            return Files.find(srcDir.toPath(), 999, predicate).collect(toList());
        } catch (IOException e) {
            log.error("Unable to find sources", e);
            return emptyList();
        }
    }

    public static void main(String[] args) {
        ParseResult parseResult = new GradleProjectParser().parse(Paths.get(args[0]));
        TreeSerializer<SourceFile> serializer = new TreeSerializer<>();
        for (SourceFile s : parseResult.getSourceFiles()) {
            System.out.println(s.getSourcePath());
            try {
                serializer.write(s, new OutputStream() {
                    private volatile boolean closed;

                    private void ensureOpen() throws IOException {
                        if (closed) {
                            throw new IOException("Stream closed");
                        }
                    }

                    @Override
                    public void write(int b) throws IOException {
                        ensureOpen();
                    }

                    @Override
                    public void write(byte[] b, int off, int len) throws IOException {
                        ensureOpen();
                    }

                    @Override
                    public void close() {
                        closed = true;
                    }
                });
            } catch (Exception e) {
                System.err.println("Error serializing " + s.getSourcePath());
                e.printStackTrace();
                throw e;
            }
        }
    }
}
