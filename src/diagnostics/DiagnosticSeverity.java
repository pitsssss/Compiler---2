package diagnostics;

/** Whether a diagnostic stops the build (ERROR) or is only advisory. */
public enum DiagnosticSeverity {
    ERROR,
    WARNING,
    INFO
}
