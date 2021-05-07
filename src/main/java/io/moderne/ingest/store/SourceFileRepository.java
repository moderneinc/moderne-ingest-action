package io.moderne.ingest.store;

import io.moderne.ingest.parse.ParseResult;

public interface SourceFileRepository {

    void store(ParseResult parseResult);
}
