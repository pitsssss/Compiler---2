package model;

import AST.ASTNode;
import AST.Program;
import AST.expr.*;
import AST.literal.*;
import AST.stmt.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Walks the Python/Flask AST and produces an {@link AppModel}. Everything is
 * read from typed AST nodes (never from source text), satisfying the rule that
 * analysis and generation are AST-driven.
 */
public final class AppModelBuilder {

    // Canonical product schema used when the data is a list-of-lists (positional).
    private static final List<String> POSITIONAL_FIELDS =
            Arrays.asList("id", "name", "price", "in_stock", "description", "category");

    public AppModel build(Program program) {
        AppModel model = new AppModel();
        if (program == null) return model;

        for (ASTNode stmt : program.getChildren()) {
            handleTopLevel(stmt, model);
        }
        // Second pass: find render_template / url_for anywhere, tracking the
        // enclosing endpoint.
        collectCalls(program, null, model);
        return model;
    }

    // ---- top-level declarations -------------------------------------------

    private void handleTopLevel(ASTNode stmt, AppModel model) {
        if (stmt instanceof FromImportStmt imp) {
            if (imp.getNames() != null) model.getImports().addAll(imp.getNames());
        } else if (stmt instanceof AssignmentStmt asg) {
            handleGlobalAssignment(asg, model);
        } else if (stmt instanceof DecoratedStmt dec) {
            handleDecorated(dec, model);
        } else if (stmt instanceof FunctionDefStmt fn) {
            model.getFunctions().put(fn.getName(), fn);
        }
    }

    private void handleGlobalAssignment(AssignmentStmt asg, AppModel model) {
        if (!(asg.getTarget() instanceof IdentifierExpr id)) return;
        model.getGlobalVars().put(id.getName(), asg.getLineNumber());

        Expression value = asg.getValue();
        // Track the Flask app variable: app = Flask(...)
        if (value instanceof CallExpr call && call.getFunction() instanceof IdentifierExpr f
                && f.getName().equals("Flask")) {
            model.setAppVariableName(id.getName());
        }
        // Extract product records from a list literal.
        if (id.getName().equals(model.getProductsVariableName()) && value instanceof ListLiteralExpr list) {
            extractProducts(list, model);
        }
    }

    private void handleDecorated(DecoratedStmt dec, AppModel model) {
        String endpoint = null;
        if (dec.getTarget() instanceof FunctionDefStmt fn) {
            endpoint = fn.getName();
            model.getFunctions().put(fn.getName(), fn);
        }
        for (Decorator d : dec.getDecorators()) {
            RouteInfo route = tryRoute(d, endpoint);
            if (route != null) model.getRoutes().add(route);
        }
    }

    /** Recognises {@code @<app>.route("/path", methods=[...])}. */
    private RouteInfo tryRoute(Decorator d, String endpoint) {
        if (!(d.getExpr() instanceof CallExpr call)) return null;
        if (!(call.getFunction() instanceof AttributeExpr attr)) return null;
        if (!attr.getAttribute().equals("route")) return null;

        String path = firstStringArg(call);
        if (path == null) return null;

        List<String> methods = new ArrayList<>();
        Expression methodsArg = call.getKeywordArguments().get("methods");
        if (methodsArg instanceof ListLiteralExpr ml) {
            for (Expression e : ml.getElements()) {
                if (e instanceof StringLiteralExpr s) methods.add(s.getUnquoted().toUpperCase());
            }
        }
        if (methods.isEmpty()) methods.add("GET");
        return new RouteInfo(path, methods, endpoint, d.getLineNumber());
    }

    // ---- render_template / url_for discovery ------------------------------

    private void collectCalls(ASTNode node, String currentFn, AppModel model) {
        if (node == null) return;

        if (node instanceof FunctionDefStmt fn) {
            currentFn = fn.getName();
        }

        if (node instanceof CallExpr call && call.getFunction() instanceof IdentifierExpr fid) {
            if (fid.getName().equals("render_template")) {
                model.getRenderCalls().add(buildRenderCall(call, currentFn));
            } else if (fid.getName().equals("url_for")) {
                String ep = firstStringArg(call);
                if (ep != null) model.getUrlForRefs().add(new UrlForRef(ep, call.getLineNumber()));
            }
        }

        for (ASTNode child : node.getChildren()) {
            collectCalls(child, currentFn, model);
        }
    }

    private RenderCall buildRenderCall(CallExpr call, String currentFn) {
        String template = firstStringArg(call);
        List<String> ctx = new ArrayList<>();

        // Positional args after the template name: capture identifier names.
        List<Expression> pos = call.getArguments();
        for (int i = 1; i < pos.size(); i++) {
            String n = contextNameOf(pos.get(i));
            if (n != null) ctx.add(n);
        }
        // Keyword args: the KEY is the context name seen inside the template.
        ctx.addAll(call.getKeywordArguments().keySet());

        return new RenderCall(template, ctx, currentFn, call.getLineNumber());
    }

    /** Best-effort context variable name for a render argument. */
    private String contextNameOf(Expression e) {
        if (e instanceof IdentifierExpr id) return id.getName();
        if (e instanceof IndexExpr ix) {
            // products[index] -> the collection name (used as a hint)
            return contextNameOf(baseOf(ix));
        }
        if (e instanceof AttributeExpr a) return a.getAttribute();
        return null;
    }

    private Expression baseOf(IndexExpr ix) {
        for (ASTNode c : ix.getChildren()) {
            if (c instanceof Expression ex) return ex;
        }
        return null;
    }

    private String firstStringArg(CallExpr call) {
        for (Expression a : call.getArguments()) {
            if (a instanceof StringLiteralExpr s) return s.getUnquoted();
        }
        return null;
    }

    // ---- product extraction -----------------------------------------------

    private void extractProducts(ListLiteralExpr list, AppModel model) {
        for (Expression element : list.getElements()) {
            if (element instanceof ListLiteralExpr row) {
                Map<String, Object> record = new LinkedHashMap<>();
                List<Expression> cells = row.getElements();
                for (int i = 0; i < cells.size(); i++) {
                    String field = (i < POSITIONAL_FIELDS.size())
                            ? POSITIONAL_FIELDS.get(i) : ("field" + i);
                    record.put(field, literalValue(cells.get(i)));
                    addField(model, field);
                }
                model.getProductRows().add(record);
            } else if (element instanceof DictLiteralExpr drow) {
                Map<String, Object> record = new LinkedHashMap<>();
                for (int i = 0; i < drow.getKeys().size(); i++) {
                    Object key = literalValue(drow.getKeys().get(i));
                    String field = String.valueOf(key);
                    record.put(field, literalValue(drow.getValues().get(i)));
                    addField(model, field);
                }
                model.getProductRows().add(record);
            }
        }
    }

    private void addField(AppModel model, String field) {
        if (!model.getProductFields().contains(field)) model.getProductFields().add(field);
    }

    /** Converts a literal expression into a Java value (String/Integer/Double/Boolean/null). */
    private Object literalValue(Expression e) {
        if (e instanceof StringLiteralExpr s) return s.getUnquoted();
        if (e instanceof BooleanLiteralExpr b) return b.getValue();
        if (e instanceof NoneLiteralExpr) return null;
        if (e instanceof NumberLiteralExpr n) {
            String raw = n.getValue();
            try {
                if (raw.contains(".") || raw.contains("e") || raw.contains("E")) {
                    return Double.parseDouble(raw);
                }
                return Integer.parseInt(raw);
            } catch (NumberFormatException ex) {
                return raw;
            }
        }
        if (e instanceof UnaryExpr u) {
            // handle simple negative numbers like -5
            for (ASTNode c : u.getChildren()) {
                if (c instanceof Expression inner) {
                    Object v = literalValue(inner);
                    if (v instanceof Integer iv) return -iv;
                    if (v instanceof Double dv) return -dv;
                }
            }
        }
        return null;
    }
}
