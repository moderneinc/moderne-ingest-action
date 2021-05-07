package io.moderne.ingest.parse;

import java.nio.file.Path;

public interface ProjectParser {

    ParseResult parse(Path projectDir);
}
