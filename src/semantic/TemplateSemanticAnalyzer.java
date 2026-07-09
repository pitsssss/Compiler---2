package semantic;

import AST.ASTNode;
import AST.template.html.HtmlAttribute;
import AST.template.html.HtmlDocument;
import AST.template.html.HtmlText;
import AST.template.jinja.expr.JinjaCallExpr;
import AST.template.jinja.expr.JinjaFilterExpr;
import AST.template.jinja.expr.JinjaIdentifierExpr;
import AST.template.jinja.stmt.JinjaBlockStmt;
import AST.template.jinja.stmt.JinjaExtendsStmt;
import AST.template.jinja.stmt.JinjaForStmt;
import AST.template.jinja.stmt.JinjaIncludeStmt;
import diagnostics.DiagnosticKind;
import diagnostics.DiagnosticReporter;
import model.TemplateDependencyGraph;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
 *   TPL007 Jinja variable used outside its scope
 *   TPL008 missing parent template in extends
 *   TPL009 template inheritance cycle
 *   TPL010 child overrides a block not declared by its parent
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

    private final Deque<Set<String>> loopVarStack = new ArrayDeque<>();
    private final Set<String> allLoopVars = new HashSet<>();

    public TemplateSemanticAnalyzer(DiagnosticReporter reporter, String file, Set<String> knownTemplates) {
        this.reporter = reporter;
        this.file = file;
        this.knownTemplates = knownTemplates == null ? Set.of() : knownTemplates;
    }

    public void analyze(ASTNode root) {
        if (root == null) return;
        loopVarStack.clear();
        allLoopVars.clear();
        collectLoopVars(root);
        checkExtendsPosition(root);

        Map<String, Integer> blockNames = new HashMap<>();
        Set<String> htmlIds = new HashSet<>();
        walk(root, blockNames, htmlIds);
    }

    /** Cross-template checks: extends parent existence, cycles, block overrides. */
    public void analyzeInheritance(Map<String, ASTNode> allTemplates) {
        if (allTemplates == null || allTemplates.isEmpty()) return;

        Map<String, Set<String>> blocksByTemplate = new HashMap<>();
        Map<String, String> extendsTarget = new HashMap<>();

        for (Map.Entry<String, ASTNode> e : allTemplates.entrySet()) {
            blocksByTemplate.put(e.getKey(), collectBlockNames(e.getValue()));
            String parent = findExtendsTarget(e.getValue());
            if (parent != null) extendsTarget.put(e.getKey(), parent);
        }

        TemplateDependencyGraph graph = new TemplateDependencyGraph();
        for (Map.Entry<String, String> e : extendsTarget.entrySet()) {
            graph.addEdge(e.getKey(), e.getValue());
        }

        for (Map.Entry<String, String> e : extendsTarget.entrySet()) {
            String child = e.getKey();
            String parent = e.getValue();
            if (!knownTemplates.isEmpty() && !knownTemplates.contains(parent)) {
                JinjaExtendsStmt ext = findExtendsNode(allTemplates.get(child));
                reporter.error(DiagnosticKind.SEMANTIC, "TPL008",
                        "Template '" + child + "' extends missing parent '" + parent + "'",
                        child, ext != null ? ext.getLineNumber() : -1, -1,
                        "create '" + parent + "' or fix the extends path");
            }
        }

        var cycle = graph.findCycle();
        if (!cycle.isEmpty()) {
            String start = cycle.get(0);
            reporter.error(DiagnosticKind.SEMANTIC, "TPL009",
                    "Template inheritance cycle detected: " + String.join(" -> ", cycle),
                    start, -1, -1,
                    "remove or rearrange extends tags to break the cycle");
        }

        for (Map.Entry<String, String> e : extendsTarget.entrySet()) {
            String child = e.getKey();
            String parent = e.getValue();
            if (!knownTemplates.contains(parent)) continue;
            Set<String> parentBlocks = blocksByTemplate.getOrDefault(parent, Set.of());
            for (String block : blocksByTemplate.getOrDefault(child, Set.of())) {
                if (!parentBlocks.contains(block)) {
                    JinjaBlockStmt b = findBlockNode(allTemplates.get(child), block);
                    reporter.error(DiagnosticKind.SEMANTIC, "TPL010",
                            "Template '" + child + "' overrides block '" + block
                                    + "' which is not declared in parent '" + parent + "'",
                            child, b != null ? b.getLineNumber() : -1, -1,
                            "add {% block " + block + " %} to '" + parent + "' or rename the block");
                }
            }
        }
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
            } else if (shouldCountAsOutput(child)) {
                sawOutput = true;
            }
        }
    }

    private boolean shouldCountAsOutput(ASTNode child) {
        if (child instanceof JinjaExtendsStmt) return false;
        if (child instanceof HtmlText t && t.getText().trim().isEmpty()) return false;
        return true;
    }

    private void collectLoopVars(ASTNode node) {
        if (node == null) return;
        if (node instanceof JinjaForStmt fs) {
            allLoopVars.addAll(fs.getVariables());
        }
        for (ASTNode child : node.getChildren()) collectLoopVars(child);
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
        } else if (node instanceof JinjaIdentifierExpr id) {
            checkLoopVarScope(id);
        } else if (node instanceof HtmlAttribute attr && attr.getName().equalsIgnoreCase("id")) {
            String value = stripQuotes(attr.getValue());
            if (!value.contains("{{") && !htmlIds.add(value)) {
                reporter.error(DiagnosticKind.SEMANTIC, "TPL005",
                        "Duplicate HTML id '" + value + "'",
                        file, attr.getLineNumber(), attr.getColumnNumber(),
                        "ids must be unique within a page");
            }
        } else if (node instanceof JinjaForStmt fs) {
            Set<String> vars = new LinkedHashSet<>(fs.getVariables());
            loopVarStack.push(vars);
            walk(fs.getIterable(), blockNames, htmlIds);
            walk(fs.getBody(), blockNames, htmlIds);
            loopVarStack.pop();
            return;
        }

        for (ASTNode child : node.getChildren()) {
            walk(child, blockNames, htmlIds);
        }
    }

    private void checkLoopVarScope(JinjaIdentifierExpr id) {
        if (!allLoopVars.contains(id.getName())) return;
        for (Set<String> frame : loopVarStack) {
            if (frame.contains(id.getName())) return;
        }
        reporter.error(DiagnosticKind.SEMANTIC, "TPL007",
                "Jinja variable '" + id.getName() + "' is used outside its scope",
                file, id.getLineNumber(), id.getColumnNumber(),
                "use '" + id.getName() + "' only inside its {% for %} loop");
    }

    private Set<String> collectBlockNames(ASTNode root) {
        Set<String> names = new LinkedHashSet<>();
        collectBlocks(root, names);
        return names;
    }

    private void collectBlocks(ASTNode node, Set<String> names) {
        if (node == null) return;
        if (node instanceof JinjaBlockStmt b) names.add(b.getName());
        for (ASTNode c : node.getChildren()) collectBlocks(c, names);
    }

    private String findExtendsTarget(ASTNode root) {
        JinjaExtendsStmt ext = findExtendsNode(root);
        return ext == null ? null : stripQuotes(ext.getTemplateName());
    }

    private JinjaExtendsStmt findExtendsNode(ASTNode root) {
        if (root == null) return null;
        if (root instanceof HtmlDocument doc) {
            for (ASTNode c : doc.getChildrenNodes()) {
                if (c instanceof JinjaExtendsStmt ext) return ext;
            }
        }
        for (ASTNode c : root.getChildren()) {
            JinjaExtendsStmt found = findExtendsNode(c);
            if (found != null) return found;
        }
        return null;
    }

    private JinjaBlockStmt findBlockNode(ASTNode root, String name) {
        if (root == null) return null;
        if (root instanceof JinjaBlockStmt b && b.getName().equals(name)) return b;
        for (ASTNode c : root.getChildren()) {
            JinjaBlockStmt found = findBlockNode(c, name);
            if (found != null) return found;
        }
        return null;
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
