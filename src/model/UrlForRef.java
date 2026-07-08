package model;

/** A {@code url_for('endpoint', ...)} reference discovered in the Flask code. */
public final class UrlForRef {
    private final String endpoint;
    private final int line;

    public UrlForRef(String endpoint, int line) {
        this.endpoint = endpoint;
        this.line = line;
    }

    public String getEndpoint() { return endpoint; }
    public int getLine() { return line; }

    @Override
    public String toString() { return "url_for('" + endpoint + "') (line " + line + ")"; }
}
