package semantic;

import AST.ASTNode;
import AST.template.html.HtmlAttribute;
import AST.template.html.HtmlText;
import AST.template.html.HtmlElement;
import AST.template.html.HtmlNormalElement;
import AST.template.html.HtmlSelfClosingElement;
import AST.template.html.HtmlVoidElement;
import AST.template.jinja.expr.JinjaAttrExpr;
import AST.template.jinja.expr.JinjaBinaryExpr;
import AST.template.jinja.expr.JinjaCallExpr;
import AST.template.jinja.expr.JinjaExpr;
import AST.template.jinja.expr.JinjaIdentifierExpr;
import AST.template.jinja.expr.JinjaStringLiteralExpr;
import AST.template.jinja.stmt.JinjaForStmt;
import diagnostics.DiagnosticKind;
import diagnostics.DiagnosticReporter;
import model.AppModel;
import model.FormModel;
import model.RenderCall;
import model.RouteSignature;
import model.TemplateContextModel;
import model.TypeInference;
import model.UrlForRef;
import model.ValueType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Links the Python/Flask side with the Jinja2 templates.
 *
 * Error catalogue (all reported as {@link DiagnosticKind#CROSS}):
 *   X001 render_template targets a template that does not exist
 *   X002 url_for references an endpoint that is not defined
 *   X003 template uses a context variable never passed by any render call
 *   X004 the details template needs 'product' but the route does not pass it
 *   X005 template accesses a product key that is not in the product schema
 *   X006 url_for missing, extra, or incorrectly named route arguments
 *   X007 HTML form method is not accepted by its target Flask route
 *   X008 HTML form field names do not match request.form fields read by backend
 *   X009 context variable type is incompatible with its template usage
 *   X010 product dictionaries have inconsistent schemas
 *   X011 product fields have inconsistent inferred types
 */
public final class CrossLanguageSemanticAnalyzer {

    private static final Set<String> IMPLICIT_NAMES = Set.of(
            "url_for", "get_flashed_messages", "range", "dict", "loop", "super",
            "config", "request", "session", "g", "True", "False", "None", "namespace"
    );

    private final DiagnosticReporter reporter;
    private final AppModel model;
    private final Map<String, ASTNode> templates;
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
        checkUrlForArguments();
        checkProductSchemaConsistency();
        checkTemplateContexts();
        checkFormsAgainstRoutes();
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
                        pyFile, ref.getLine(), ref.getColumn(),
                        "add a route function named '" + ref.getEndpoint() + "'");
            }
        }
        for (Map.Entry<String, ASTNode> e : templates.entrySet()) {
            if (e.getValue() != null) {
                collectTemplateUrlForCalls(e.getValue(), e.getKey());
            }
        }
    }

    // X006
    private void checkUrlForArguments() {
        List<UrlForRef> allRefs = new ArrayList<>(model.getUrlForRefs());
        for (Map.Entry<String, ASTNode> e : templates.entrySet()) {
            allRefs.addAll(collectTemplateUrlForRefs(e.getValue(), e.getKey()));
            allRefs.addAll(collectEmbeddedUrlForRefs(e.getValue(), e.getKey()));
        }
        for (UrlForRef ref : allRefs) {
            RouteSignature route = model.routeForEndpoint(ref.getEndpoint());
            if (route == null) continue;
            Set<String> required = new LinkedHashSet<>(route.getPathParameters());
            Set<String> provided = ref.getKeywordArgNames();

            for (String param : required) {
                if (!provided.contains(param)) {
                    String loc = ref.getFile() != null ? ref.getFile() : pyFile;
                    reporter.error(DiagnosticKind.CROSS, "X006",
                            "url_for('" + ref.getEndpoint() + "') is missing required route argument '"
                                    + param + "'",
                            loc, ref.getLine(), ref.getColumn(),
                            "pass " + param + "=...");
                }
            }
            for (String kw : provided) {
                if (!required.contains(kw)) {
                    String loc = ref.getFile() != null ? ref.getFile() : pyFile;
                    reporter.error(DiagnosticKind.CROSS, "X006",
                            "url_for('" + ref.getEndpoint() + "') has unexpected route argument '"
                                    + kw + "'",
                            loc, ref.getLine(), ref.getColumn(),
                            "remove '" + kw + "' or fix the route path");
                }
            }
        }
    }

    // X010, X011
    private void checkProductSchemaConsistency() {
        if (model.getProductRows().isEmpty()) return;
        var schema = model.getProductSchema();
        if (schema.hasInconsistentSchemas()) {
            reporter.error(DiagnosticKind.CROSS, "X010",
                    "Product dictionaries have inconsistent schemas across rows",
                    pyFile, -1, -1,
                    "ensure every product record uses the same keys");
        }
        if (schema.hasInconsistentTypes()) {
            reporter.error(DiagnosticKind.CROSS, "X011",
                    "Product fields have inconsistent inferred types across rows",
                    pyFile, -1, -1,
                    "use the same value type for each field in every product");
        }
    }

    // X003, X004, X005, X009
    private void checkTemplateContexts() {
        Set<String> productFields = new HashSet<>(model.getProductFields());

        for (RenderCall rc : model.getRenderCalls()) {
            ASTNode root = templates.get(rc.getTemplateName());
            if (root == null) continue;

            Set<String> provided = new LinkedHashSet<>(rc.getContextNames());
            Set<String> loopVars = new LinkedHashSet<>();
            Set<String> usedRootVars = new LinkedHashSet<>();
            Map<String, Set<String>> attrsByVar = new LinkedHashMap<>();

            collect(root, loopVars, usedRootVars, attrsByVar);

            String tname = rc.getTemplateName();
            TemplateContextModel ctxModel = buildContextModel(rc);

            // X003
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

            // X004
            if (attrsByVar.containsKey("product")
                    && !provided.contains("product") && !loopVars.contains("product")) {
                reporter.error(DiagnosticKind.CROSS, "X004",
                        "Template '" + tname + "' uses 'product' but " + rc.getEndpoint()
                                + "() does not pass it",
                        pyFile, rc.getLine(), -1,
                        "render with product=...");
            }

            // X005
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

            // X009
            checkContextTypes(root, ctxModel, loopVars, tname, rc.getLine());
        }
    }

    private TemplateContextModel buildContextModel(RenderCall rc) {
        TemplateContextModel ctx = new TemplateContextModel();
        for (String name : rc.getContextNames()) {
            ValueType t = ValueType.UNKNOWN;
            if (name.equals(model.getProductsVariableName()) || name.equals("products")) {
                t = ValueType.LIST;
            } else if (name.equals("product")) {
                t = ValueType.DICT;
            } else if (name.equals("errors")) {
                t = ValueType.LIST;
            }
            ctx.put(name, t);
        }
        return ctx;
    }

    private void checkContextTypes(ASTNode node, TemplateContextModel ctx,
                                   Set<String> loopVars, String tname, int pyLine) {
        if (node == null) return;
        if (node instanceof JinjaAttrExpr attr) {
            JinjaExpr target = attr.getTarget();
            if (target instanceof JinjaIdentifierExpr id) {
                ValueType baseType = resolveVarType(id.getName(), ctx, loopVars);
                ValueType required = TypeInference.requiredBaseTypeForAttribute(attr.getAttribute());
                if (required != ValueType.UNKNOWN && baseType != ValueType.UNKNOWN
                        && baseType != required) {
                    reporter.error(DiagnosticKind.CROSS, "X009",
                            "Context variable '" + id.getName() + "' (" + baseType
                                    + ") is incompatible with ." + attr.getAttribute()
                                    + " in template '" + tname + "'",
                            tname, attr.getLineNumber(), attr.getColumnNumber(),
                            "pass a " + required.toString().toLowerCase() + " or change the template");
                }
            }
        }
        for (ASTNode c : node.getChildren()) {
            checkContextTypes(c, ctx, loopVars, tname, pyLine);
        }
    }

    private ValueType resolveVarType(String name, TemplateContextModel ctx, Set<String> loopVars) {
        ValueType t = ctx.get(name);
        if (t != ValueType.UNKNOWN) return t;
        if (loopVars.contains(name)) return ValueType.DICT;
        return ValueType.UNKNOWN;
    }

    // X007, X008
    private void checkFormsAgainstRoutes() {
        List<FormModel> forms = new ArrayList<>();
        for (Map.Entry<String, ASTNode> e : templates.entrySet()) {
            forms.addAll(collectForms(e.getValue(), e.getKey()));
        }
        for (FormModel form : forms) {
            String endpoint = form.getEndpoint();
            if (endpoint == null || !model.hasEndpoint(endpoint)) continue;

            RouteSignature route = model.routeForEndpoint(endpoint);
            if (route == null) continue;

            String method = form.getMethod().toUpperCase();
            if (!route.getMethods().contains(method)) {
                reporter.error(DiagnosticKind.CROSS, "X007",
                        "Form in '" + form.getTemplateFile() + "' uses method " + method
                                + " but route '" + endpoint + "' accepts " + route.getMethods(),
                        form.getTemplateFile(), form.getLine(), form.getColumn(),
                        "change the form method or add " + method + " to the route");
            }

            Set<String> backendFields = model.getRequestFormFieldsByEndpoint()
                    .getOrDefault(endpoint, Set.of());
            if (backendFields.isEmpty()) continue;

            Set<String> formFields = form.getFieldNameSet();
            for (String field : formFields) {
                if (!backendFields.contains(field)) {
                    reporter.error(DiagnosticKind.CROSS, "X008",
                            "Form field '" + field + "' in '" + form.getTemplateFile()
                                    + "' is not read by " + endpoint + "() via request.form",
                            form.getTemplateFile(), form.getLine(), form.getColumn(),
                            "add request.form.get(\"" + field + "\") or remove the field");
                }
            }
            for (String read : backendFields) {
                if (!formFields.contains(read)) {
                    reporter.error(DiagnosticKind.CROSS, "X008",
                            endpoint + "() reads request.form field '" + read
                                    + "' but no matching input exists in the form",
                            form.getTemplateFile(), form.getLine(), form.getColumn(),
                            "add <input name=\"" + read + "\"> to the form");
                }
            }
        }
    }

    private List<FormModel> collectForms(ASTNode node, String templateFile) {
        List<FormModel> out = new ArrayList<>();
        collectFormsRecursive(node, templateFile, out);
        return out;
    }

    private void collectFormsRecursive(ASTNode node, String templateFile, List<FormModel> out) {
        if (node == null) return;
        if (isFormElement(node)) {
            HtmlElement formEl = (HtmlElement) node;
            String method = "get";
            String action = null;
            for (HtmlAttribute a : formEl.getAttributes()) {
                if (a.getName().equalsIgnoreCase("method")) {
                    method = stripQuotes(a.getValue());
                } else if (a.getName().equalsIgnoreCase("action")) {
                    action = a.getValue();
                }
            }
            FormModel fm = new FormModel(templateFile, method,
                    extractUrlForEndpoint(action), formEl.getLineNumber(), formEl.getColumnNumber());
            collectFormFields(formEl, fm);
            out.add(fm);
        }
        for (ASTNode c : node.getChildren()) collectFormsRecursive(c, templateFile, out);
    }

    private boolean isFormElement(ASTNode node) {
        return node instanceof HtmlNormalElement el && "form".equalsIgnoreCase(el.getTagName());
    }

    private void collectFormFields(ASTNode node, FormModel form) {
        if (node == null) return;
        if (node instanceof HtmlElement el) {
            String tag = tagName(el);
            if ("input".equalsIgnoreCase(tag) || "textarea".equalsIgnoreCase(tag)
                    || "select".equalsIgnoreCase(tag)) {
                for (HtmlAttribute a : el.getAttributes()) {
                    if (a.getName().equalsIgnoreCase("name")) {
                        form.addField(stripQuotes(a.getValue()));
                    }
                }
            }
        }
        for (ASTNode c : node.getChildren()) collectFormFields(c, form);
    }

    private String tagName(HtmlElement el) {
        return el.getTagName();
    }

    private void collectTemplateUrlForCalls(ASTNode node, String templateFile) {
        if (node == null) return;
        if (node instanceof JinjaCallExpr call
                && call.getCallee() instanceof JinjaIdentifierExpr id
                && id.getName().equals("url_for")) {
            String endpoint = firstStringArg(call);
            if (endpoint != null && !model.hasEndpoint(endpoint)) {
                reporter.error(DiagnosticKind.CROSS, "X002",
                        "url_for references undefined endpoint '" + endpoint + "'",
                        templateFile, call.getLineNumber(), call.getColumnNumber(),
                        "add a route function named '" + endpoint + "'");
            }
        }
        for (ASTNode c : node.getChildren()) collectTemplateUrlForCalls(c, templateFile);
    }

    private List<UrlForRef> collectTemplateUrlForRefs(ASTNode node, String templateFile) {
        List<UrlForRef> refs = new ArrayList<>();
        collectTemplateUrlForRefsRecursive(node, templateFile, refs);
        return refs;
    }

    private List<UrlForRef> collectEmbeddedUrlForRefs(ASTNode node, String templateFile) {
        List<UrlForRef> refs = new ArrayList<>();
        collectEmbeddedUrlForRefsRecursive(node, templateFile, refs);
        return refs;
    }

    private void collectEmbeddedUrlForRefsRecursive(ASTNode node, String templateFile, List<UrlForRef> refs) {
        if (node instanceof HtmlAttribute attr) {
            addEmbeddedUrlFor(attr.getValue(), templateFile, attr.getLineNumber(), attr.getColumnNumber(), refs);
        } else if (node instanceof HtmlText text) {
            addEmbeddedUrlFor(text.getText(), templateFile, text.getLineNumber(), -1, refs);
        }
        for (ASTNode c : node.getChildren()) {
            collectEmbeddedUrlForRefsRecursive(c, templateFile, refs);
        }
    }

    /** Parses {@code {{ url_for('ep', kw=val) }}} embedded in HTML text/attributes. */
    private void addEmbeddedUrlFor(String raw, String templateFile, int line, int column, List<UrlForRef> refs) {
        if (raw == null || !raw.contains("url_for")) return;
        int searchFrom = 0;
        while (true) {
            int idx = raw.indexOf("url_for", searchFrom);
            if (idx < 0) break;
            int open = raw.indexOf('(', idx);
            if (open < 0) break;
            String endpoint = readQuotedString(raw, open + 1);
            if (endpoint == null) break;
            Set<String> kw = readUrlForKeywordNames(raw, open);
            refs.add(new UrlForRef(endpoint, line, column, templateFile, kw));
            searchFrom = open + 1;
        }
    }

    private String readQuotedString(String raw, int from) {
        for (int i = from; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\'' || c == '"') {
                int end = raw.indexOf(c, i + 1);
                if (end > i + 1) return raw.substring(i + 1, end);
                return null;
            }
        }
        return null;
    }

    private Set<String> readUrlForKeywordNames(String raw, int openParen) {
        Set<String> kw = new LinkedHashSet<>();
        int close = findMatchingParen(raw, openParen);
        if (close < 0) return kw;
        String inside = raw.substring(openParen + 1, close);
        int i = 0;
        boolean afterFirstArg = false;
        while (i < inside.length()) {
            char c = inside.charAt(i);
            if (c == '\'' || c == '"') {
                afterFirstArg = true;
                i = inside.indexOf(c, i + 1);
                if (i < 0) break;
                i++;
                continue;
            }
            if (afterFirstArg && Character.isLetter(c)) {
                int start = i;
                while (i < inside.length() && (Character.isLetterOrDigit(inside.charAt(i))
                        || inside.charAt(i) == '_')) i++;
                int eq = i;
                while (eq < inside.length() && Character.isWhitespace(inside.charAt(eq))) eq++;
                if (eq < inside.length() && inside.charAt(eq) == '=') {
                    kw.add(inside.substring(start, i));
                }
                continue;
            }
            if (c == ',') afterFirstArg = true;
            i++;
        }
        return kw;
    }

    private int findMatchingParen(String raw, int open) {
        int depth = 0;
        for (int i = open; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '(') depth++;
            else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private void collectTemplateUrlForRefsRecursive(ASTNode node, String templateFile, List<UrlForRef> refs) {
        if (node instanceof JinjaCallExpr call
                && call.getCallee() instanceof JinjaIdentifierExpr id
                && id.getName().equals("url_for")) {
            String endpoint = firstStringArg(call);
            if (endpoint != null) {
                refs.add(new UrlForRef(endpoint, call.getLineNumber(), call.getColumnNumber(),
                        templateFile, extractJinjaKwArgs(call)));
            }
        }
        for (ASTNode c : node.getChildren()) collectTemplateUrlForRefsRecursive(c, templateFile, refs);
    }

    private Set<String> extractJinjaKwArgs(JinjaCallExpr call) {
        Set<String> kw = new LinkedHashSet<>();
        for (JinjaExpr arg : call.getArgs()) {
            if (arg instanceof JinjaBinaryExpr bin && "=".equals(bin.getOp().trim())) {
                if (bin.getLeft() instanceof JinjaIdentifierExpr id) {
                    kw.add(id.getName());
                }
            }
        }
        return kw;
    }

    private String firstStringArg(JinjaCallExpr call) {
        for (JinjaExpr a : call.getArgs()) {
            if (a instanceof JinjaStringLiteralExpr s) {
                return stripQuotes(s.getRawText());
            }
        }
        return null;
    }

    /** Extracts endpoint name from an HTML attribute embedding {@code url_for('...')}. */
    private String extractUrlForEndpoint(String raw) {
        if (raw == null) return null;
        int idx = raw.indexOf("url_for");
        if (idx < 0) return null;
        int start = -1;
        for (int i = idx; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\'' || c == '"') {
                start = i + 1;
                char quote = c;
                int end = raw.indexOf(quote, start);
                if (end > start) return raw.substring(start, end);
                break;
            }
        }
        return null;
    }

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

    private void recordAttr(JinjaAttrExpr attr, Map<String, Set<String>> attrsByVar) {
        JinjaExpr target = attr.getTarget();
        if (target instanceof JinjaIdentifierExpr id) {
            attrsByVar.computeIfAbsent(id.getName(), k -> new LinkedHashSet<>())
                      .add(attr.getAttribute());
        } else if (target instanceof JinjaAttrExpr inner) {
            recordAttr(inner, attrsByVar);
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
