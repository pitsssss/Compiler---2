package model;

import java.util.List;

/** Arity information for a user-defined Python function. */
public final class FunctionSignature {
    private final String name;
    private final List<String> parameters;
    private final int line;

    public FunctionSignature(String name, List<String> parameters, int line) {
        this.name = name;
        this.parameters = List.copyOf(parameters);
        this.line = line;
    }

    public String getName() { return name; }
    public List<String> getParameters() { return parameters; }
    public int getParameterCount() { return parameters.size(); }
    public int getLine() { return line; }
}
