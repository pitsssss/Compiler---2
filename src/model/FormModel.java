package model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** An HTML {@code <form>} discovered in a template AST. */
public final class FormModel {
    private final String templateFile;
    private final String method;
    private final String endpoint; // resolved from url_for in action, may be null
    private final List<String> fieldNames = new ArrayList<>();
    private final int line;
    private final int column;

    public FormModel(String templateFile, String method, String endpoint,
                     int line, int column) {
        this.templateFile = templateFile;
        this.method = method == null ? "get" : method.toLowerCase();
        this.endpoint = endpoint;
        this.line = line;
        this.column = column;
    }

    public void addField(String name) {
        if (name != null && !name.isBlank()) fieldNames.add(name);
    }

    public String getTemplateFile() { return templateFile; }
    public String getMethod() { return method; }
    public String getEndpoint() { return endpoint; }
    public List<String> getFieldNames() { return List.copyOf(fieldNames); }
    public Set<String> getFieldNameSet() { return new LinkedHashSet<>(fieldNames); }
    public int getLine() { return line; }
    public int getColumn() { return column; }
}
