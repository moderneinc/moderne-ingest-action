package io.moderne.ingest.store;

import com.google.cloud.WriteChannel;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.common.net.MediaType;
import io.moderne.ingest.parse.ParseResult;
import org.apache.commons.compress.compressors.lzma.LZMACompressorOutputStream;
import org.openrewrite.SourceFile;
import org.openrewrite.TreeSerializer;
import org.openrewrite.marker.GitProvenance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

public class GcsSourceFileRepository implements SourceFileRepository {

    private static final String BUCKET_NAME = "moderne-ast";
    private final Logger log = LoggerFactory.getLogger(GcsSourceFileRepository.class);
    private final Storage storage;

    public GcsSourceFileRepository(Storage storage) {
        this.storage = storage;
    }

    @Override
    public void store(ParseResult parseResult) {

        GitProvenance gitProvenance = parseResult.getGitProvenance();
        String blobName = gitProvenance.getOrganizationName() + "/" + gitProvenance.getRepositoryName() + ".ast";
        TreeSerializer<SourceFile> serializer = new TreeSerializer<>();
        Path tempFile;
        try {
            tempFile = Files.createTempFile("moderne-ingest", ".ast");
            try (OutputStream outputStream = Files.newOutputStream(tempFile);
                 LZMACompressorOutputStream lzma = new LZMACompressorOutputStream(outputStream)) {
                serializer.write(parseResult.getSourceFiles(), lzma);
                lzma.flush();
                outputStream.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        log.info("Writing AST to {} bucket at {}", BUCKET_NAME, blobName);
        BlobInfo blobInfo = BlobInfo.newBuilder(BUCKET_NAME, blobName).setContentType("application/octet-stream").build();
        try (WriteChannel writer = storage.writer(blobInfo)) {
            byte[] buffer = new byte[1024];
            try (InputStream input = Files.newInputStream(tempFile, StandardOpenOption.DELETE_ON_CLOSE)) {
                int limit;
                while ((limit = input.read(buffer)) >= 0) {
                    writer.write(ByteBuffer.wrap(buffer, 0, limit));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
