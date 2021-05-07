package io.moderne.ingest.parse;

import org.openrewrite.SourceFile;
import org.openrewrite.marker.GitProvenance;

import java.util.List;

public class ParseResult {

    private final GitProvenance gitProvenance;
    private List<SourceFile> sourceFiles;

    public ParseResult(GitProvenance gitProvenance, List<SourceFile> sourceFiles) {
        this.gitProvenance = gitProvenance;
        this.sourceFiles = sourceFiles;
    }

    public GitProvenance getGitProvenance() { return gitProvenance; }

    public List<SourceFile> getSourceFiles() {
        return sourceFiles;
    }

    public void setSourceFiles(List<SourceFile> sourceFiles) {
        this.sourceFiles = sourceFiles;
    }
}
