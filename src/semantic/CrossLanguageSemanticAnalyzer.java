package semantic;

import AST.ASTNode;
import AST.template.jinja.expr.JinjaAttrExpr;
import AST.template.jinja.expr.JinjaExpr;
import AST.template.jinja.expr.JinjaIdentifierExpr;
import AST.template.jinja.stmt.JinjaForStmt;
import diagnostics.DiagnosticKind;
import diagnostics.DiagnosticReporter;
import model.AppModel;
import model.RenderCall;
import model.UrlForRef;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Links the Python/Flask side with the Jinja2 templates — the required proof
 * that the two ASTs are connected rather than analysed in isolation.
 *
 * Error catalogue (all reported as {@link DiagnosticKind#CROSS}):
 *   X001 render_template targets a template that does not exist
 *   X002 url_for references an endpoint that is not defined
 *   X003 template uses a context variable never passed by any render call
 *   X004 the details template needs 'product' but the route does not pass it
 *   X005 template accesses a product key that is not in the product schema
 */
public final class CrossLanguageSemanticAnalyzer {

    private static final Set<String> IMPLICIT_NAMES = Set.of(
            "url_for", "get_flashed_messages", "range", "dict", "loop", "super",
            "config", "request", "session", "g", "True", "False", "None", "namespace"
    );

    private final DiagnosticReporter reporter;
    private final AppModel model;
    private final Map<String, ASTNode> templates;     // name -> template AST root
    private final Set<String> knownTemplateNames;
    private final String pyFile;

    public CrossLanguageSemanticAnalyzer(DiagnosticReporter reporter, AppModel model,
                                         Map<String, ASTNode> templates, String pyFile) {
        this.reporter = reporter;
        this.model = model;
        this.templates = templates == null ? Map.of() : templates;
        this.knownTemplateNames = this.templates.keySet();
        this.pyFile = pyFile;
    }

    public void analyze() {
        checkRenderTargets();
        checkUrlForEndpoints();
        checkTemplateContexts();
    }

    // X001
    private void checkRenderTargets() {
        for (RenderCall rc : model.getRenderCalls()) {
            if (!knownTemplateNames.contains(rc.getTemplateName())) {
                reporter.error(DiagnosticKind.CROSS, "X001",
                        "render_template targets missing template '" + rc.getTemplateName() + "'",
                        pyFile, rc.getLine(), -1,
                        "create the template or fix its name");
            }
        }
    }

    // X002
    private void checkUrlForEndpoints() {
        for (UrlForRef ref : model.getUrlForRefs()) {
            if (!model.hasEndpoint(ref.getEndpoint())) {
                reporter.error(DiagnosticKind.CROSS, "X002",
                        "url_for references undefined endpoint '" + ref.getEndpoint() + "'",
                        pyFile, ref.getLine(), -1,
                        "add a route function named '" + ref.getEndpoint() + "'");
            }
        }
    }

    // X003, X004, X005 — for templates that are directly rendered.
    private void checkTemplateContexts() {
        Set<String> productFields = new HashSet<>(model.getProductFields());

        for (RenderCall rc : model.getRenderCalls()) {
            ASTNode root = templates.get(rc.getTemplateName());
            if (root == null) continue; // already reported as X001

            Set<String> provided = new LinkedHashSet<>(rc.getContextNames());
            Set<String> loopVars = new LinkedHashSet<>();
            Set<String> usedRootVars = new LinkedHashSet<>();
            Map<String, Set<String>> attrsByVar = new LinkedHashMap<>();

            collect(root, loopVars, usedRootVars, attrsByVar);

            String tname = rc.getTemplateName();

            // X003: any used root variable that isn't provided / loop-local / implicit.
            for (String var : usedRootVars) {
                if (IMPLICIT_NAMES.contains(var)) continue;
                if (loopVars.contains(var)) continue;
                if (provided.contains(var)) continue;
                reporter.error(DiagnosticKind.CROSS, "X003",
                        "Template '" + tname + "' uses context variable '" + var
                                + "' not passed by " + rc.getEndpoint() + "()",
                        pyFile, rc.getLine(), -1,
                        "pass " + var + "=... in render_template");
            }

            // X004: a details-style template that needs 'product'.
            if (attrsByVar.containsKey("product")
                    && !provided.contains("product") && !loopVars.contains("product")) {
                reporter.error(DiagnosticKind.CROSS, "X004",
                        "Template '" + tname + "' uses 'product' but " + rc.getEndpoint()
                                + "() does not pass it",
                        pyFile, rc.getLine(), -1,
                        "render with product=... ");
            }

            // X005: product attribute not present in the extracted schema.
            if (!productFields.isEmpty()) {
                for (Map.Entry<String, Set<String>> e : attrsByVar.entrySet()) {
                    String var = e.getKey();
                    boolean isProductItem = var.equals("product")
                            || (loopVars.contains(var) && iteratesProducts(root, var));
                    if (!isProductItem) continue;
                    for (String attr : e.getValue()) {
                        if (!productFields.contains(attr)) {
                            reporter.error(DiagnosticKind.CROSS, "X005",
                                    "Template '" + tname + "' accesses product key '" + attr
                                            + "' which is not in the product schema " + model.getProductFields(),
                                    pyFile, rc.getLine(), -1,
                                    "add '" + attr + "' to each product or fix the template");
                        }
                    }
                }
            }
        }
    }

    /** True if some {% for var in products %} iterates the products collection. */
    private boolean iteratesProducts(ASTNode node, String var) {
        if (node instanceof JinjaForStmt fs && fs.getVariables().contains(var)) {
            for (String v : fs.getIterable().getVariables()) {
                if (v.equals(model.getProductsVariableName())) return true;
            }
        }
        for (ASTNode c : node.getChildren()) {
            if (iteratesProducts(c, var)) return true;
        }
        return false;
    }

    private void collect(ASTNode node, Set<String> loopVars, Set<String> usedRootVars,
                         Map<String, Set<String>> attrsByVar) {
        if (node == null) return;

        if (node instanceof JinjaForStmt fs) {
            loopVars.addAll(fs.getVariables());
        }

        if (node instanceof JinjaExpr expr) {
            usedRootVars.addAll(expr.getVariables());
        }

        if (node instanceof JinjaAttrExpr attr) {
            recordAttr(attr, attrsByVar);
        }

        for (ASTNode c : node.getChildren()) {
            collect(c, loopVars, usedRootVars, attrsByVar);
        }
    }

    /** Records the attribute nearest the root identifier (product.name -> product:name). */
    private void recordAttr(JinjaAttrExpr attr, Map<String, Set<String>> attrsByVar) {
        JinjaExpr target = attr.getTarget();
        if (target instanceof JinjaIdentifierExpr id) {
            attrsByVar.computeIfAbsent(id.getName(), k -> new LinkedHashSet<>())
                      .add(attr.getAttribute());
        } else if (target instanceof JinjaAttrExpr inner) {
            recordAttr(inner, attrsByVar);
        }
    }
}
