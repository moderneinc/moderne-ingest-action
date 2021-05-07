package io.moderne.ingest.store;

import java.io.InputStream;
import java.util.List;

public interface StyleRepository {

    InputStream getStyleInputStream(String styleName);
}
