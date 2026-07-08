package model;

import java.util.List;

/** A {@code render_template("x.html", ...)} call discovered in the Flask code. */
public final class RenderCall {
    private final String templateName;      // "products.html"
    private final List<String> contextNames;// names of context variables passed
    private final String endpoint;          // enclosing function
    private final int line;

    public RenderCall(String templateName, List<String> contextNames, String endpoint, int line) {
        this.templateName = templateName;
        this.contextNames = contextNames;
        this.endpoint = endpoint;
        this.line = line;
    }

    public String getTemplateName() { return templateName; }
    public List<String> getContextNames() { return contextNames; }
    public String getEndpoint() { return endpoint; }
    public int getLine() { return line; }

    @Override
    public String toString() {
        return "render " + templateName + " ctx=" + contextNames
                + " in " + endpoint + " (line " + line + ")";
    }
}
