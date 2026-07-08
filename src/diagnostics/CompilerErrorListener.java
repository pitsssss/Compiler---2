package diagnostics;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.Lexer;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/**
 * Replaces ANTLR's default console error listener. Every lexer/parser error is
 * turned into a {@link Diagnostic} and pushed to the shared reporter, so that
 * lexical and syntax problems are surfaced with file/line/column instead of
 * being silently ignored.
 */
public final class CompilerErrorListener extends BaseErrorListener {

    private final DiagnosticReporter reporter;
    private final String file;

    public CompilerErrorListener(DiagnosticReporter reporter, String file) {
        this.reporter = reporter;
        this.file = file;
    }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                            int line, int charPositionInLine, String msg,
                            RecognitionException e) {

        boolean fromLexer = recognizer instanceof Lexer;
        DiagnosticKind kind = fromLexer ? DiagnosticKind.LEXICAL : DiagnosticKind.SYNTAX;
        String code = fromLexer ? "LEX001" : "SYN001";

        String suggestion = null;
        if (!fromLexer && recognizer instanceof Parser) {
            suggestion = "check for a missing token near this position";
        } else if (fromLexer) {
            suggestion = "unexpected character(s) — remove or quote them";
        }

        reporter.error(kind, code, msg, file, line, charPositionInLine, suggestion);
    }
}
