package model;

import java.util.List;

/** A Flask route discovered from an {@code @app.route(...)} decorator. */
public final class RouteInfo {
    private final String path;         // e.g. "/products"
    private final List<String> methods;// e.g. ["GET"] or ["GET","POST"]
    private final String endpoint;     // the decorated function name
    private final int line;

    public RouteInfo(String path, List<String> methods, String endpoint, int line) {
        this.path = path;
        this.methods = methods;
        this.endpoint = endpoint;
        this.line = line;
    }

    public String getPath() { return path; }
    public List<String> getMethods() { return methods; }
    public String getEndpoint() { return endpoint; }
    public int getLine() { return line; }

    @Override
    public String toString() {
        return endpoint + " " + methods + " " + path + " (line " + line + ")";
    }
}
