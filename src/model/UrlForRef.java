package model;

import java.util.LinkedHashSet;
import java.util.Set;

/** A {@code url_for('endpoint', ...)} reference in Python or a template. */
public final class UrlForRef {
    private final String endpoint;
    private final int line;
    private final int column;
    private final String file;
    private final Set<String> keywordArgNames;

    public UrlForRef(String endpoint, int line, int column, String file, Set<String> keywordArgNames) {
        this.endpoint = endpoint;
        this.line = line;
        this.column = column;
        this.file = file;
        this.keywordArgNames = keywordArgNames == null ? Set.of() : Set.copyOf(keywordArgNames);
    }

    /** Backward-compatible constructor for Python-side refs without column/file/kwargs. */
    public UrlForRef(String endpoint, int line) {
        this(endpoint, line, -1, null, Set.of());
    }

    public String getEndpoint() { return endpoint; }
    public int getLine() { return line; }
    public int getColumn() { return column; }
    public String getFile() { return file; }
    public Set<String> getKeywordArgNames() { return keywordArgNames; }

    @Override
    public String toString() {
        return "url_for('" + endpoint + "') kwargs=" + keywordArgNames + " (line " + line + ")";
    }
}
