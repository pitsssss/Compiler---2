package semantic;

import AST.ASTNode;
import AST.template.html.HtmlAttribute;
import AST.template.html.HtmlDocument;
import AST.template.jinja.expr.JinjaCallExpr;
import AST.template.jinja.expr.JinjaFilterExpr;
import AST.template.jinja.expr.JinjaIdentifierExpr;
import AST.template.jinja.stmt.JinjaBlockStmt;
import AST.template.jinja.stmt.JinjaExtendsStmt;
import AST.template.jinja.stmt.JinjaIncludeStmt;
import diagnostics.DiagnosticKind;
import diagnostics.DiagnosticReporter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Semantic analysis for a single template AST (HTML + CSS + Jinja2).
 *
 * Error catalogue (all reported as {@link DiagnosticKind#SEMANTIC}):
 *   TPL001 duplicate {% block %} name
 *   TPL002 {% extends %} in an invalid position (must be the first node)
 *   TPL003 {% include %} of a template that does not exist
 *   TPL004 unknown Jinja filter
 *   TPL005 duplicate HTML id on the page
 *   TPL006 unknown Jinja global function
 */
public final class TemplateSemanticAnalyzer {

    private static final Set<String> KNOWN_FILTERS = Set.of(
            "default", "truncate", "format", "upper", "lower", "length", "count",
            "capitalize", "title", "trim", "join", "replace", "safe", "e", "escape",
            "round", "int", "float", "string", "list", "first", "last", "sort",
            "reverse", "abs", "tojson", "wordwrap", "center", "urlencode", "striptags"
    );

    private static final Set<String> KNOWN_GLOBALS = Set.of(
            "url_for", "get_flashed_messages", "range", "dict", "lipsum", "cycler",
            "namespace", "super", "config", "request", "session", "g"
    );

    private final DiagnosticReporter reporter;
    private final String file;
    private final Set<String> knownTemplates;

    public TemplateSemanticAnalyzer(DiagnosticReporter reporter, String file, Set<String> knownTemplates) {
        this.reporter = reporter;
        this.file = file;
        this.knownTemplates = knownTemplates == null ? Set.of() : knownTemplates;
    }

    public void analyze(ASTNode root) {
        if (root == null) return;
        checkExtendsPosition(root);

        Map<String, Integer> blockNames = new HashMap<>();
        Set<String> htmlIds = new HashSet<>();
        walk(root, blockNames, htmlIds);
    }

    private void checkExtendsPosition(ASTNode root) {
        if (!(root instanceof HtmlDocument doc)) return;
        boolean sawOutput = false;
        for (ASTNode child : doc.getChildrenNodes()) {
            if (child instanceof JinjaExtendsStmt ext) {
                if (sawOutput) {
                    reporter.error(DiagnosticKind.SEMANTIC, "TPL002",
                            "{% extends %} must be the first thing in a template",
                            file, ext.getLineNumber(), ext.getColumnNumber(),
                            "move the extends tag to the very top");
                }
            } else {
                // Anything else at the top level counts as output/content.
                sawOutput = true;
            }
        }
    }

    private void walk(ASTNode node, Map<String, Integer> blockNames, Set<String> htmlIds) {
        if (node == null) return;

        if (node instanceof JinjaBlockStmt block) {
            Integer prev = blockNames.put(block.getName(), block.getLineNumber());
            if (prev != null) {
                reporter.error(DiagnosticKind.SEMANTIC, "TPL001",
                        "Duplicate block name '" + block.getName() + "'",
                        file, block.getLineNumber(), block.getColumnNumber(),
                        "block names must be unique within a template");
            }
        } else if (node instanceof JinjaIncludeStmt inc) {
            String name = stripQuotes(inc.getTemplateName());
            if (!knownTemplates.isEmpty() && !knownTemplates.contains(name)) {
                reporter.error(DiagnosticKind.SEMANTIC, "TPL003",
                        "Included template '" + name + "' does not exist",
                        file, inc.getLineNumber(), inc.getColumnNumber(),
                        "create the template or fix the name");
            }
        } else if (node instanceof JinjaFilterExpr filter) {
            if (!KNOWN_FILTERS.contains(filter.getFilterName())) {
                reporter.error(DiagnosticKind.SEMANTIC, "TPL004",
                        "Unknown Jinja filter '" + filter.getFilterName() + "'",
                        file, filter.getLineNumber(), filter.getColumnNumber(),
                        "check the filter name or register it");
            }
        } else if (node instanceof JinjaCallExpr call
                && call.getCallee() instanceof JinjaIdentifierExpr callee) {
            if (!KNOWN_GLOBALS.contains(callee.getName())) {
                reporter.error(DiagnosticKind.SEMANTIC, "TPL006",
                        "Unknown Jinja function '" + callee.getName() + "'",
                        file, call.getLineNumber(), call.getColumnNumber(),
                        "only Jinja globals like url_for/range are available");
            }
        } else if (node instanceof HtmlAttribute attr && attr.getName().equalsIgnoreCase("id")) {
            String value = stripQuotes(attr.getValue());
            // Skip dynamic ids that contain Jinja expressions.
            if (!value.contains("{{") && !htmlIds.add(value)) {
                reporter.error(DiagnosticKind.SEMANTIC, "TPL005",
                        "Duplicate HTML id '" + value + "'",
                        file, attr.getLineNumber(), attr.getColumnNumber(),
                        "ids must be unique within a page");
            }
        }

        for (ASTNode child : node.getChildren()) {
            walk(child, blockNames, htmlIds);
        }
    }

    private String stripQuotes(String s) {
        if (s == null || s.length() < 2) return s == null ? "" : s;
        char first = s.charAt(0), last = s.charAt(s.length() - 1);
        if ((first == '"' || first == '\'') && first == last) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }
}
