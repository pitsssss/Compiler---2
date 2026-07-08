package diagnostics;

/**
 * A single compiler message. Immutable value object carrying everything the
 * user needs: phase (kind), severity, a specific error code, a clear message,
 * the file, the line/column, and an optional short fix suggestion.
 */
public final class Diagnostic {

    private final DiagnosticKind kind;
    private final DiagnosticSeverity severity;
    private final String code;        // e.g. PY001, TPL003, X002
    private final String message;
    private final String file;        // source file name (may be null)
    private final int line;           // 1-based; -1 when unknown
    private final int column;         // 0-based; -1 when unknown
    private final String suggestion;  // may be null

    public Diagnostic(DiagnosticKind kind, DiagnosticSeverity severity, String code,
                      String message, String file, int line, int column, String suggestion) {
        this.kind = kind;
        this.severity = severity;
        this.code = code;
        this.message = message;
        this.file = file;
        this.line = line;
        this.column = column;
        this.suggestion = suggestion;
    }

    public DiagnosticKind getKind() { return kind; }
    public DiagnosticSeverity getSeverity() { return severity; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public String getFile() { return file; }
    public int getLine() { return line; }
    public int getColumn() { return column; }
    public String getSuggestion() { return suggestion; }

    /** Human-readable single line, e.g.
     *  {@code [SEMANTIC/ERROR PY001] app.py:12:4 Undefined variable 'x' (hint: define it first)}. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(kind).append('/').append(severity);
        if (code != null) sb.append(' ').append(code);
        sb.append("] ");
        String loc = (file != null ? file : "<input>");
        if (line >= 0) {
            loc += ":" + line;
            if (column >= 0) loc += ":" + column;
        }
        sb.append(loc).append(' ').append(message);
        if (suggestion != null && !suggestion.isEmpty()) {
            sb.append(" (hint: ").append(suggestion).append(')');
        }
        return sb.toString();
    }
}
