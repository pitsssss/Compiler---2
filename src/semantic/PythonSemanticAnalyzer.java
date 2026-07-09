package semantic;

import AST.ASTNode;
import AST.Program;
import AST.expr.*;
import AST.literal.*;
import AST.stmt.*;
import AST.suite.Suite;
import diagnostics.DiagnosticKind;
import diagnostics.DiagnosticReporter;
import model.AppModel;
import model.FunctionSignature;
import model.RouteSignature;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Semantic analysis for the Python/Flask AST.
 *
 * Error catalogue (all reported as {@link DiagnosticKind#SEMANTIC}):
 *   PY001 undefined variable
 *   PY002 duplicate function definition in the same scope
 *   PY003 duplicate parameter name
 *   PY004 'return' outside a function
 *   PY005 'break'/'continue' outside a loop
 *   PY006 call to an undefined function
 *   PY007 duplicate route path
 *   PY008 function argument count mismatch
 *   PY009 Flask route path parameters do not match function parameters
 *   PY010 local variable used before assignment
 *   PY011 Flask route has an execution path without return
 */
public final class PythonSemanticAnalyzer {

    private static final Set<String> BUILTINS = Set.of(
            "len", "int", "str", "list", "dict", "bool", "float", "print", "range",
            "abs", "min", "max", "sum", "sorted", "enumerate", "type", "isinstance",
            "super", "object", "map", "filter", "zip", "round", "set", "tuple",
            "Flask", "render_template", "request", "redirect", "url_for", "jsonify",
            "session", "flash", "abort", "make_response", "send_file", "Blueprint",
            "g", "render_template_string"
    );

    private final DiagnosticReporter reporter;
    private final String file;
    private final AppModel model;
    private AppModel activeModel;
    private final Set<String> knownNames = new HashSet<>();
    private final Deque<Set<String>> scopes = new ArrayDeque<>();

    private int functionDepth = 0;
    private int loopDepth = 0;

    /** Locals declared in the current function (params + assignment targets). */
    private Set<String> functionLocals = Set.of();
    /** Locals definitely assigned so far in the current function body walk. */
    private Set<String> assignedLocals = new HashSet<>();

    public PythonSemanticAnalyzer(DiagnosticReporter reporter, String file) {
        this(reporter, file, null);
    }

    public PythonSemanticAnalyzer(DiagnosticReporter reporter, String file, AppModel model) {
        this.reporter = reporter;
        this.file = file;
        this.model = model;
    }

    public void analyze(Program program, AppModel model) {
        if (program == null) return;

        knownNames.addAll(BUILTINS);
        AppModel m = model != null ? model : this.model;
        activeModel = m;
        if (m != null) knownNames.addAll(m.getImports());

        Set<String> global = new LinkedHashSet<>();
        collectGlobalNames(program, global);
        scopes.push(global);

        detectDuplicateTopLevel(program);
        if (m != null) {
            checkRouteParameterAlignment(m);
            checkRouteReturns(m);
        }

        for (ASTNode stmt : program.getChildren()) {
            checkStmt(stmt);
        }
        scopes.pop();
    }

    // ---- declaration collection -------------------------------------------

    private void collectGlobalNames(Program program, Set<String> out) {
        for (ASTNode stmt : program.getChildren()) {
            if (stmt instanceof FromImportStmt imp && imp.getNames() != null) {
                out.addAll(imp.getNames());
            } else if (stmt instanceof AssignmentStmt asg
                    && asg.getTarget() instanceof IdentifierExpr id) {
                out.add(id.getName());
            } else if (stmt instanceof FunctionDefStmt fn) {
                out.add(fn.getName());
            } else if (stmt instanceof DecoratedStmt dec
                    && dec.getTarget() instanceof FunctionDefStmt fn) {
                out.add(fn.getName());
            } else if (stmt instanceof ClassDefStmt cd) {
                out.add(cd.getName());
            }
        }
    }

    private void collectAssignmentTargets(ASTNode node, Set<String> out) {
        if (node == null) return;
        if (node instanceof AssignmentStmt asg) {
            addTargetName(asg.getTarget(), out);
        } else if (node instanceof AssignmentChainStmt chain) {
            for (ASTNode c : chain.getChildren()) {
                if (c instanceof Expression tgt) addTargetName(tgt, out);
            }
        } else if (node instanceof ForStmt fs) {
            out.add(fs.getIterator().getName());
            collectAssignmentTargets(fs.getBody(), out);
            return;
        } else if (node instanceof FunctionDefStmt || node instanceof ClassDefStmt) {
            return;
        }
        for (ASTNode child : node.getChildren()) {
            collectAssignmentTargets(child, out);
        }
    }

    private void addTargetName(Expression target, Set<String> out) {
        if (target instanceof IdentifierExpr id) out.add(id.getName());
    }

    private void detectDuplicateTopLevel(Program program) {
        Set<String> seenFns = new HashSet<>();
        Set<String> seenPaths = new HashSet<>();
        for (ASTNode stmt : program.getChildren()) {
            FunctionDefStmt fn = null;
            if (stmt instanceof FunctionDefStmt f) fn = f;
            else if (stmt instanceof DecoratedStmt dec && dec.getTarget() instanceof FunctionDefStmt f) fn = f;

            if (fn != null && !seenFns.add(fn.getName())) {
                reporter.error(DiagnosticKind.SEMANTIC, "PY002",
                        "Duplicate function definition '" + fn.getName() + "'",
                        file, fn.getLineNumber(), fn.getColumnNumber(),
                        "rename one of the functions");
            }

            if (stmt instanceof DecoratedStmt dec) {
                for (Decorator d : dec.getDecorators()) {
                    String path = routePath(d);
                    if (path != null && !seenPaths.add(path)) {
                        reporter.error(DiagnosticKind.SEMANTIC, "PY007",
                                "Duplicate route path '" + path + "'",
                                file, d.getLineNumber(), d.getColumnNumber(),
                                "each @app.route path must be unique");
                    }
                }
            }
        }
    }

    private String routePath(Decorator d) {
        if (d.getExpr() instanceof CallExpr call
                && call.getFunction() instanceof AttributeExpr attr
                && attr.getAttribute().equals("route")) {
            for (Expression a : call.getArguments()) {
                if (a instanceof StringLiteralExpr s) return s.getUnquoted();
            }
        }
        return null;
    }

    // ---- route-specific checks (PY009, PY011) -----------------------------

    private void checkRouteParameterAlignment(AppModel m) {
        for (RouteSignature route : m.getRouteSignatures().values()) {
            FunctionDefStmt fn = m.getFunctions().get(route.getEndpoint());
            if (fn == null) continue;
            List<String> fnParams = fn.getParameters();
            for (String pathParam : route.getPathParameters()) {
                if (!fnParams.contains(pathParam)) {
                    reporter.error(DiagnosticKind.SEMANTIC, "PY009",
                            "Route '" + route.getPath() + "' expects path parameter '"
                                    + pathParam + "' but function '" + route.getEndpoint()
                                    + "()' does not declare it",
                            file, route.getLine(), -1,
                            "add parameter '" + pathParam + "' to " + route.getEndpoint());
                }
            }
        }
    }

    private void checkRouteReturns(AppModel m) {
        for (RouteSignature route : m.getRouteSignatures().values()) {
            FunctionDefStmt fn = m.getFunctions().get(route.getEndpoint());
            if (fn == null) continue;
            if (!allPathsReturn(fn.getBody())) {
                reporter.error(DiagnosticKind.SEMANTIC, "PY011",
                        "Route handler '" + route.getEndpoint() + "()' may finish without returning a response",
                        file, fn.getLineNumber(), fn.getColumnNumber(),
                        "ensure every execution path ends with return");
            }
        }
    }

    private boolean allPathsReturn(Suite suite) {
        if (suite == null) return false;
        return suiteAllPathsReturn(suite);
    }

    private boolean suiteAllPathsReturn(Suite suite) {
        List<ASTNode> stmts = suite.getChildren();
        for (int i = 0; i < stmts.size(); i++) {
            ASTNode stmt = stmts.get(i);
            if (stmt instanceof ReturnStmt) return true;
            if (stmt instanceof IfStmt ifs) {
                boolean thenOk = allPathsReturn(ifs.getThenSuite());
                if (ifs.getElseSuite() != null) {
                    if (thenOk && allPathsReturn(ifs.getElseSuite())) return true;
                } else if (thenOk && i == stmts.size() - 1) {
                    return false; // if-return without else at end — fall-through path
                }
            }
        }
        return false;
    }

    // ---- statement checking -----------------------------------------------

    private void checkStmt(ASTNode stmt) {
        if (stmt == null) return;

        if (stmt instanceof FunctionDefStmt fn) {
            checkFunction(fn);
        } else if (stmt instanceof DecoratedStmt dec) {
            for (Decorator d : dec.getDecorators()) checkExpr(d.getExpr());
            checkStmt(dec.getTarget());
        } else if (stmt instanceof ReturnStmt ret) {
            if (functionDepth == 0) {
                reporter.error(DiagnosticKind.SEMANTIC, "PY004",
                        "'return' used outside of a function",
                        file, ret.getLineNumber(), ret.getColumnNumber(),
                        "move this return inside a function");
            }
            if (ret.getValue() != null) checkExpr(ret.getValue());
        } else if (stmt instanceof BreakStmt bs) {
            if (loopDepth == 0) {
                reporter.error(DiagnosticKind.SEMANTIC, "PY005",
                        "'break' used outside of a loop",
                        file, bs.getLineNumber(), bs.getColumnNumber(), null);
            }
        } else if (stmt instanceof ContinueStmt cs) {
            if (loopDepth == 0) {
                reporter.error(DiagnosticKind.SEMANTIC, "PY005",
                        "'continue' used outside of a loop",
                        file, cs.getLineNumber(), cs.getColumnNumber(), null);
            }
        } else if (stmt instanceof ForStmt fs) {
            checkExpr(fs.getIterable());
            currentScope().add(fs.getIterator().getName());
            markAssigned(fs.getIterator().getName());
            loopDepth++;
            checkSuite(fs.getBody());
            loopDepth--;
        } else if (stmt instanceof WhileStmt ws) {
            checkChildrenExprAndSuites(ws, true);
        } else if (stmt instanceof IfStmt ifs) {
            checkChildrenExprAndSuites(ifs, false);
        } else if (stmt instanceof AssignmentStmt asg) {
            checkExpr(asg.getValue());
            checkAssignTarget(asg.getTarget());
        } else if (stmt instanceof AssignmentChainStmt chain) {
            List<ASTNode> ch = chain.getChildren();
            for (int i = 0; i < ch.size() - 1; i++) {
                if (ch.get(i) instanceof Expression tgt) checkAssignTarget(tgt);
            }
            if (!ch.isEmpty() && ch.get(ch.size() - 1) instanceof Expression val) checkExpr(val);
        } else if (stmt instanceof ExpressionStmt es) {
            checkExpr(es.getExpression());
        } else if (stmt instanceof ClassDefStmt) {
            // out of scope
        } else if (stmt instanceof Suite s) {
            checkSuite(s);
        }
    }

    private void checkFunction(FunctionDefStmt fn) {
        Set<String> seen = new HashSet<>();
        Set<String> params = new LinkedHashSet<>();
        if (fn.getParameters() != null) {
            for (String p : fn.getParameters()) {
                if (!seen.add(p)) {
                    reporter.error(DiagnosticKind.SEMANTIC, "PY003",
                            "Duplicate parameter name '" + p + "' in function '" + fn.getName() + "'",
                            file, fn.getLineNumber(), fn.getColumnNumber(),
                            "give each parameter a unique name");
                }
                params.add(p);
            }
        }

        Set<String> locals = new LinkedHashSet<>(params);
        collectAssignmentTargets(fn.getBody(), locals);

        Set<String> savedLocals = functionLocals;
        Set<String> savedAssigned = assignedLocals;
        functionLocals = locals;
        assignedLocals = new HashSet<>(params);

        Set<String> localScope = new LinkedHashSet<>(params);
        scopes.push(localScope);
        int savedLoop = loopDepth;
        loopDepth = 0;
        functionDepth++;
        checkSuite(fn.getBody());
        functionDepth--;
        loopDepth = savedLoop;
        scopes.pop();

        functionLocals = savedLocals;
        assignedLocals = savedAssigned;
    }

    private void checkChildrenExprAndSuites(ASTNode node, boolean isLoop) {
        for (ASTNode child : node.getChildren()) {
            if (child instanceof Suite s) {
                if (isLoop) loopDepth++;
                checkSuite(s);
                if (isLoop) loopDepth--;
            } else if (child instanceof Expression e) {
                checkExpr(e);
            }
        }
    }

    private void checkSuite(Suite suite) {
        if (suite == null) return;
        for (ASTNode s : suite.getChildren()) {
            checkStmt(s);
        }
    }

    private void checkAssignTarget(Expression target) {
        if (target instanceof IdentifierExpr id) {
            currentScope().add(id.getName());
            markAssigned(id.getName());
        } else if (target instanceof AttributeExpr a) {
            checkExpr(a.getBase());
        } else if (target instanceof IndexExpr ix) {
            for (ASTNode c : ix.getChildren()) if (c instanceof Expression e) checkExpr(e);
        }
    }

    private void markAssigned(String name) {
        if (functionDepth > 0) assignedLocals.add(name);
    }

    // ---- expression checking ----------------------------------------------

    private void checkExpr(Expression expr) {
        if (expr == null) return;

        if (expr instanceof IdentifierExpr id) {
            resolveIdentifier(id);
        } else if (expr instanceof CallExpr call) {
            checkCall(call);
        } else if (expr instanceof AttributeExpr a) {
            checkExpr(a.getBase());
        } else if (expr instanceof LambdaExpr lambda) {
            Set<String> lscope = new LinkedHashSet<>();
            if (lambda.getParams() != null) lscope.addAll(lambda.getParams());
            scopes.push(lscope);
            checkExpr(lambda.getBody());
            scopes.pop();
        } else {
            for (ASTNode c : expr.getChildren()) {
                if (c instanceof Expression e) checkExpr(e);
            }
        }
    }

    private void resolveIdentifier(IdentifierExpr id) {
        String name = id.getName();
        if (functionDepth > 0 && functionLocals.contains(name) && !assignedLocals.contains(name)) {
            reporter.error(DiagnosticKind.SEMANTIC, "PY010",
                    "Local variable '" + name + "' is used before assignment",
                    file, id.getLineNumber(), id.getColumnNumber(),
                    "assign to '" + name + "' before reading it");
            return;
        }
        if (!isDefined(name)) {
            reporter.error(DiagnosticKind.SEMANTIC, "PY001",
                    "Undefined variable '" + name + "'",
                    file, id.getLineNumber(), id.getColumnNumber(),
                    "define '" + name + "' before using it");
        }
    }

    private void checkCall(CallExpr call) {
        Expression fn = call.getFunction();
        if (fn instanceof IdentifierExpr id) {
            if (!isDefined(id.getName())) {
                reporter.error(DiagnosticKind.SEMANTIC, "PY006",
                        "Call to undefined function '" + id.getName() + "'",
                        file, id.getLineNumber(), id.getColumnNumber(),
                        "define or import '" + id.getName() + "'");
            } else {
                checkArgumentCount(id, call);
            }
        } else {
            checkExpr(fn);
        }
        for (Expression a : call.getArguments()) checkExpr(a);
        for (Expression a : call.getKeywordArguments().values()) checkExpr(a);
    }

    private void checkArgumentCount(IdentifierExpr callee, CallExpr call) {
        if (activeModel == null) return;
        FunctionSignature sig = activeModel.getFunctionSignatures().get(callee.getName());
        if (sig == null || BUILTINS.contains(callee.getName())) return;

        List<String> params = sig.getParameters();
        int pos = call.getArguments().size();
        Set<String> kw = call.getKeywordArguments().keySet();

        if (pos > params.size()) {
            reporter.error(DiagnosticKind.SEMANTIC, "PY008",
                    "Call to '" + callee.getName() + "()' has too many positional arguments "
                            + "(expected " + params.size() + ", got " + pos + ")",
                    file, call.getLineNumber(), call.getColumnNumber(),
                    "pass exactly " + params.size() + " argument(s)");
            return;
        }

        for (int i = pos; i < params.size(); i++) {
            if (!kw.contains(params.get(i))) {
                reporter.error(DiagnosticKind.SEMANTIC, "PY008",
                        "Call to '" + callee.getName() + "()' is missing required argument '"
                                + params.get(i) + "'",
                        file, call.getLineNumber(), call.getColumnNumber(),
                        "pass " + params.get(i) + "=...");
                return;
            }
        }

        for (String k : kw) {
            if (!params.contains(k)) {
                reporter.error(DiagnosticKind.SEMANTIC, "PY008",
                        "Call to '" + callee.getName() + "()' has unexpected keyword argument '"
                                + k + "'",
                        file, call.getLineNumber(), call.getColumnNumber(),
                        "remove '" + k + "' or fix the parameter name");
            }
        }
    }

    private Set<String> currentScope() { return scopes.peek(); }

    private boolean isDefined(String name) {
        if (knownNames.contains(name)) return true;
        for (Set<String> scope : scopes) {
            if (scope.contains(name)) return true;
        }
        return false;
    }
}
