package diagnostics;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects diagnostics from every phase and prints an organized, grouped report.
 * Analyzers never print directly; they report here so output stays consistent
 * and the pipeline can decide success/failure from the error count.
 */
public final class DiagnosticReporter {

    private final List<Diagnostic> diagnostics = new ArrayList<>();

    // ---- reporting helpers -------------------------------------------------

    public void report(Diagnostic d) {
        diagnostics.add(d);
    }

    public void error(DiagnosticKind kind, String code, String message,
                      String file, int line, int column, String suggestion) {
        report(new Diagnostic(kind, DiagnosticSeverity.ERROR, code, message, file, line, column, suggestion));
    }

    public void warning(DiagnosticKind kind, String code, String message,
                        String file, int line, int column, String suggestion) {
        report(new Diagnostic(kind, DiagnosticSeverity.WARNING, code, message, file, line, column, suggestion));
    }

    // ---- queries -----------------------------------------------------------

    public List<Diagnostic> all() { return new ArrayList<>(diagnostics); }

    public long countErrors() {
        return diagnostics.stream().filter(d -> d.getSeverity() == DiagnosticSeverity.ERROR).count();
    }

    public long countWarnings() {
        return diagnostics.stream().filter(d -> d.getSeverity() == DiagnosticSeverity.WARNING).count();
    }

    public boolean hasErrors() { return countErrors() > 0; }

    public List<Diagnostic> byKind(DiagnosticKind kind) {
        List<Diagnostic> out = new ArrayList<>();
        for (Diagnostic d : diagnostics) if (d.getKind() == kind) out.add(d);
        return out;
    }

    // ---- output ------------------------------------------------------------

    /** Prints all diagnostics grouped by phase, then a summary line. */
    public void printReport() {
        System.out.println();
        System.out.println("==================== DIAGNOSTICS ====================");
        if (diagnostics.isEmpty()) {
            System.out.println("No diagnostics. Everything looks good.");
        } else {
            printGroup("Lexical errors", DiagnosticKind.LEXICAL);
            printGroup("Syntax errors", DiagnosticKind.SYNTAX);
            printGroup("Semantic errors/warnings", DiagnosticKind.SEMANTIC);
            printGroup("Cross-language (linking) errors/warnings", DiagnosticKind.CROSS);
        }
        System.out.println("-----------------------------------------------------");
        System.out.println("Errors: " + countErrors() + "   Warnings: " + countWarnings());
        System.out.println("=====================================================");
    }

    private void printGroup(String title, DiagnosticKind kind) {
        List<Diagnostic> group = byKind(kind);
        if (group.isEmpty()) return;
        System.out.println();
        System.out.println("-- " + title + " (" + group.size() + ") --");
        for (Diagnostic d : group) {
            System.out.println("  " + d);
        }
    }
}
