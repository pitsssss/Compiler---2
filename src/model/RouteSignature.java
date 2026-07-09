package model;

import java.util.ArrayList;
import java.util.List;

/** Flask route metadata including path parameters extracted from the route path literal. */
public final class RouteSignature {
    private final String endpoint;
    private final String path;
    private final List<String> pathParameters;
    private final List<String> methods;
    private final int line;

    public RouteSignature(String endpoint, String path, List<String> methods, int line) {
        this.endpoint = endpoint;
        this.path = path;
        this.pathParameters = extractPathParameters(path);
        this.methods = List.copyOf(methods);
        this.line = line;
    }

    public String getEndpoint() { return endpoint; }
    public String getPath() { return path; }
    public List<String> getPathParameters() { return pathParameters; }
    public List<String> getMethods() { return methods; }
    public int getLine() { return line; }

    /** Parses {@code /items/<id>} and {@code /items/<int:id>} style segments. */
    public static List<String> extractPathParameters(String path) {
        List<String> out = new ArrayList<>();
        if (path == null) return out;
        int i = 0;
        while (i < path.length()) {
            if (path.charAt(i) == '<') {
                int end = path.indexOf('>', i);
                if (end < 0) break;
                String segment = path.substring(i + 1, end);
                int colon = segment.indexOf(':');
                out.add(colon >= 0 ? segment.substring(colon + 1) : segment);
                i = end + 1;
            } else {
                i++;
            }
        }
        return out;
    }
}
