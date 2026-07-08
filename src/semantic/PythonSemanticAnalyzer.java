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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Semantic analysis for the Python/Flask AST. Performs its own scoped walk so
 * that scoping, use-before-definition and control-flow rules can be checked,
 * and reports every problem it finds (it does not stop at the first error).
 *
 * Error catalogue (all reported as {@link DiagnosticKind#SEMANTIC}):
 *   PY001 undefined variable
 *   PY002 duplicate function definition in the same scope
 *   PY003 duplicate parameter name
 *   PY004 'return' outside a function
 *   PY005 'break'/'continue' outside a loop
 *   PY006 call to an undefined function
 *   PY007 duplicate route path
 */
public final class PythonSemanticAnalyzer {

    private static final Set<String> BUILTINS = Set.of(
            "len", "int", "str", "list", "dict", "bool", "float", "print", "range",
            "abs", "min", "max", "sum", "sorted", "enumerate", "type", "isinstance",
            "super", "object", "map", "filter", "zip", "round", "set", "tuple",
            // Flask surface
            "Flask", "render_template", "request", "redirect", "url_for", "jsonify",
            "session", "flash", "abort", "make_response", "send_file", "Blueprint",
            "g", "render_template_string"
    );

    private final DiagnosticReporter reporter;
    private final String file;
    private final Set<String> knownNames = new HashSet<>(); // globals + imports + builtins
    private final Deque<Set<String>> scopes = new ArrayDeque<>();

    private int functionDepth = 0;
    private int loopDepth = 0;

    public PythonSemanticAnalyzer(DiagnosticReporter reporter, String file) {
        this.reporter = reporter;
        this.file = file;
    }

    public void analyze(Program program, AppModel model) {
        if (program == null) return;

        knownNames.addAll(BUILTINS);
        if (model != null) knownNames.addAll(model.getImports());

        // Global scope: collect every top-level binding first (module names are
        // resolvable regardless of textual order, matching Python's semantics).
        Set<String> global = new LinkedHashSet<>();
        collectGlobalNames(program, global);
        scopes.push(global);

        detectDuplicateTopLevel(program);

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

    /** Names bound within a function body (params handled separately), stopping
     *  at nested function/class boundaries. */
    private void collectBoundNames(ASTNode node, Set<String> out) {
        if (node == null) return;
        if (node instanceof AssignmentStmt asg) {
            if (asg.getTarget() instanceof IdentifierExpr id) out.add(id.getName());
        } else if (node instanceof AssignmentChainStmt chain) {
            for (ASTNode c : chain.getChildren()) {
                if (c instanceof IdentifierExpr id) out.add(id.getName());
            }
        } else if (node instanceof ForStmt fs) {
            out.add(fs.getIterator().getName());
            collectBoundNames(fs.getBody(), out);
            return;
        } else if (node instanceof FunctionDefStmt fn) {
            out.add(fn.getName());
            return; // do not descend into nested function
        } else if (node instanceof ClassDefStmt) {
            return;
        }
        for (ASTNode child : node.getChildren()) {
            collectBoundNames(child, out);
        }
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
            // The loop variable is bound in the enclosing (function/global) scope.
            currentScope().add(fs.getIterator().getName());
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
            // Class bodies are out of scope for this Python subset's checks.
        } else if (stmt instanceof Suite s) {
            checkSuite(s);
        }
        // pass/global/del/import: nothing to resolve here
    }

    private void checkFunction(FunctionDefStmt fn) {
        // PY003 duplicate parameter names
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

        Set<String> localScope = new LinkedHashSet<>(params);
        collectBoundNames(fn.getBody(), localScope);
        scopes.push(localScope);
        int savedLoop = loopDepth;
        loopDepth = 0;           // loops don't cross the function boundary
        functionDepth++;
        checkSuite(fn.getBody());
        functionDepth--;
        loopDepth = savedLoop;
        scopes.pop();
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
        } else if (target instanceof AttributeExpr a) {
            checkExpr(a.getBase());
        } else if (target instanceof IndexExpr ix) {
            for (ASTNode c : ix.getChildren()) if (c instanceof Expression e) checkExpr(e);
        }
    }

    // ---- expression checking ----------------------------------------------

    private void checkExpr(Expression expr) {
        if (expr == null) return;

        if (expr instanceof IdentifierExpr id) {
            if (!isDefined(id.getName())) {
                reporter.error(DiagnosticKind.SEMANTIC, "PY001",
                        "Undefined variable '" + id.getName() + "'",
                        file, id.getLineNumber(), id.getColumnNumber(),
                        "define '" + id.getName() + "' before using it");
            }
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
            // BinaryExpr, CompareExpr, UnaryExpr, NotExpr, IndexExpr, literals,
            // list/set/dict literals: recurse into expression children.
            for (ASTNode c : expr.getChildren()) {
                if (c instanceof Expression e) checkExpr(e);
            }
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
            }
        } else {
            // method call obj.m(...) or chained call: check the callee expression
            checkExpr(fn);
        }
        for (Expression a : call.getArguments()) checkExpr(a);
        for (Expression a : call.getKeywordArguments().values()) checkExpr(a);
    }

    // ---- scope helpers -----------------------------------------------------

    private Set<String> currentScope() { return scopes.peek(); }

    private boolean isDefined(String name) {
        if (knownNames.contains(name)) return true;
        for (Set<String> scope : scopes) {
            if (scope.contains(name)) return true;
        }
        return false;
    }
}
