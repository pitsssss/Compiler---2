package diagnostics;

/**
 * The compiler phase a diagnostic originates from. This is what lets the
 * report clearly separate Lexical vs Syntax vs Semantic vs cross-language
 * (linking) problems, as required.
 */
public enum DiagnosticKind {
    LEXICAL,   // token-level problems (from the lexer)
    SYNTAX,    // grammar-level problems (from the parser)
    SEMANTIC,  // meaning-level problems inside one language
    CROSS      // linking problems between Python and the templates
}
