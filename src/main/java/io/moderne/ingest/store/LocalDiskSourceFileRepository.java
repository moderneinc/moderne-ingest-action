package io.moderne.ingest.store;

import io.moderne.ingest.parse.ParseResult;
import org.apache.commons.compress.compressors.lzma.LZMACompressorOutputStream;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeSerializer;
import org.openrewrite.marker.GitProvenance;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

@Profile("!prod")
@Component
public class LocalDiskSourceFileRepository implements SourceFileRepository {

    @Override
    public void store(ParseResult parseResult) {
        GitProvenance gitProvenance = parseResult.getGitProvenance();
        List<SourceFile> sourceFiles = parseResult.getSourceFiles();
        if (!sourceFiles.isEmpty()) {
            File astFile = new File(System.getProperty("user.home"),
                    "rewrite-offline/" + gitProvenance.getOrganizationName() + "/" +
                            gitProvenance.getRepositoryName() + ".ast.tmp");

            if (astFile.exists()) {
                if (!astFile.delete()) {
                    throw new RuntimeException("Expected to be able to delete existing Rewrite JAR");
                }
            }

            if (!astFile.getParentFile().exists() && !astFile.getParentFile().mkdirs()) {
                throw new RuntimeException("Expected to be able to create directory for Rewrite JAR");
            }

            TreeSerializer<SourceFile> serializer = new TreeSerializer<>();

            try (FileOutputStream fos = new FileOutputStream(astFile);
                 LZMACompressorOutputStream lzma = new LZMACompressorOutputStream(fos)) {
                serializer.write(sourceFiles, lzma);
                lzma.flush();
                fos.flush();
            } catch (IOException e) {
                throw new RuntimeException("Failed to write AST file", e);
            }

            File astCompleteFile = new File(System.getProperty("user.home"), "rewrite-offline/" +
                    gitProvenance.getOrganizationName() + "/" + gitProvenance.getRepositoryName() + ".ast");
            if (!(astFile.renameTo(astCompleteFile))) {
                throw new RuntimeException("Unable to rename AST temp file " + astFile.getAbsolutePath() +
                        " to final filename " + astCompleteFile.getAbsolutePath());
            }
        }
    }
}
