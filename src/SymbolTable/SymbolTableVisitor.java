package SymbolTable;

import AST.ASTNode;
import AST.Program;
import AST.expr.*;
import AST.stmt.*;
import AST.suite.Suite;

import java.lang.reflect.Field;
import java.util.*;

public class SymbolTableVisitor {

    private final FlaskSymbolTable symbolTable;
    private boolean debugMode = false;
    private final Set<String> processedImports = new HashSet<>();
    private final Set<String> processedBuiltins = new HashSet<>();

    public SymbolTableVisitor() {
        this.symbolTable = new FlaskSymbolTable();
    }

    public SymbolTableVisitor(boolean debugMode) {
        this();
        this.debugMode = debugMode;
    }

    public FlaskSymbolTable getSymbolTable() {
        return symbolTable;
    }


    public void visitProgram(Program program) {
        log("Visiting Program");
        visitStatements(program.getChildren());
    }

    private void visitStatements(List<ASTNode> statements) {
        for (ASTNode stmt : statements) {
            if (stmt != null) {
                visitStatement(stmt);
            }
        }
    }

    private void visitStatement(ASTNode stmt) {
        if (stmt == null) return;

        log("Visiting statement: " + stmt.getClass().getSimpleName() + " at line " + stmt.getLineNumber());

        if (stmt instanceof AssignmentStmt) {
            visitAssignment((AssignmentStmt) stmt);
        } else if (stmt instanceof FunctionDefStmt) {
            visitFunction((FunctionDefStmt) stmt);
        } else if (stmt instanceof ClassDefStmt) {
            visitClass((ClassDefStmt) stmt);
        } else if (stmt instanceof IfStmt) {
            visitIf((IfStmt) stmt);
        } else if (stmt instanceof WhileStmt) {
            visitWhile((WhileStmt) stmt);
        } else if (stmt instanceof ForStmt) {
            visitFor((ForStmt) stmt);
        } else if (stmt instanceof ReturnStmt) {
            visitReturn((ReturnStmt) stmt);
        } else if (stmt instanceof ExpressionStmt) {
            visitExpressionStmt((ExpressionStmt) stmt);
        } else if (stmt instanceof FromImportStmt) {
            visitFromImport((FromImportStmt) stmt);
        } else if (stmt instanceof GlobalStmt) {
            visitGlobal((GlobalStmt) stmt);
        } else if (stmt instanceof DecoratedStmt) {
            visitDecorated((DecoratedStmt) stmt);
        } else if (stmt instanceof AssignmentChainStmt) {
            visitAssignmentChain((AssignmentChainStmt) stmt);
        } else if (stmt instanceof Suite) {
            visitSuite((Suite) stmt);
        }
    }


    private void visitFromImport(FromImportStmt stmt) {
        log("Visiting FromImport at line " + stmt.getLineNumber());

        // Use the typed AST fields directly (no string parsing).
        List<String> names = stmt.getNames();
        if (names == null) return;
        for (String name : names) {
            if (name == null || name.trim().isEmpty()) continue;
            String key = name + "_" + stmt.getLineNumber();
            if (processedImports.add(key)) {
                symbolTable.addImport(name.trim(), stmt.getLineNumber());
                log("Added import: " + name + " at line " + stmt.getLineNumber());
            }
        }
    }

    private void visitAssignment(AssignmentStmt stmt) {
        log("Visiting Assignment at line " + stmt.getLineNumber());

        if (stmt.getChildren().size() < 2) return;

        ASTNode target = stmt.getChildren().get(0);

        if (target instanceof IdentifierExpr) {
            String varName = ((IdentifierExpr) target).getName();

            if (isInGlobalScope()) {
                symbolTable.addGlobalVariable(varName, stmt.getLineNumber());
            } else {
                symbolTable.addLocalVariable(varName, stmt.getLineNumber());
            }
        } else if (target instanceof AttributeExpr) {
            visitAttributeExpression((AttributeExpr) target);
        }

        visitExpression(stmt.getChildren().get(1));
    }

    private void visitFunction(FunctionDefStmt stmt) {
        log("Visiting Function: " + stmt.getName() + " at line " + stmt.getLineNumber());

        symbolTable.addFunction(stmt.getName(), stmt.getParameters(), stmt.getLineNumber());
        symbolTable.enterScope(stmt.getName(), FlaskSymbolTable.Scope.ScopeType.FUNCTION);

        if (stmt.getParameters() != null) {
            for (String param : stmt.getParameters()) {
                if (param != null && !param.trim().isEmpty()) {
                    symbolTable.addParameter(param, stmt.getLineNumber());
                }
            }
        }

        if (!stmt.getChildren().isEmpty()) {
            ASTNode body = stmt.getChildren().get(0);
            if (body instanceof Suite) {
                visitSuite((Suite) body);
            }
        }

        symbolTable.exitScope();
    }

    private void visitDecorated(DecoratedStmt stmt) {
        log("Visiting Decorated statement at line " + stmt.getLineNumber());

        List<ASTNode> children = stmt.getChildren();
        if (children.isEmpty()) return;

        for (ASTNode child : children) {
            if (child instanceof Decorator) {
                List<ASTNode> decoratorChildren = child.getChildren();
                if (!decoratorChildren.isEmpty()) {
                    ASTNode decoratorExpr = decoratorChildren.get(0);

                    // استخراج وتخزين سمة app.route
                    if (decoratorExpr instanceof AttributeExpr) {
                        processDecoratorAttribute((AttributeExpr) decoratorExpr, child.getLineNumber());
                    }

                    visitExpression(decoratorExpr);
                }
            }
        }

        ASTNode lastChild = children.get(children.size() - 1);
        if (lastChild instanceof Statement) {
            visitStatement((Statement) lastChild);
        }
    }

    private void processDecoratorAttribute(AttributeExpr attr, int lineNumber) {
        String base = dottedBaseName(attr.getBase());
        String attribute = attr.getAttribute();
        symbolTable.addAttribute(base, attribute, lineNumber);
        log("Processed decorator: " + base + "." + attribute + " at line " + lineNumber);
    }

    private void visitExpression(ASTNode expr) {
        if (expr == null) return;

        log("Visiting expression: " + expr.getClass().getSimpleName() + " at line " + expr.getLineNumber());

        if (expr instanceof IdentifierExpr) {
            visitIdentifierExpression((IdentifierExpr) expr);
        } else if (expr instanceof CallExpr) {
            visitCallExpression((CallExpr) expr);
        } else if (expr instanceof AttributeExpr) {
            visitAttributeExpression((AttributeExpr) expr);
        } else if (expr instanceof BinaryExpr) {
            visitBinaryExpression((BinaryExpr) expr);
        } else if (expr instanceof CompareExpr) {
            visitCompareExpression((CompareExpr) expr);
        }

        for (ASTNode child : expr.getChildren()) {
            visitExpression(child);
        }
    }

    private void visitIdentifierExpression(IdentifierExpr expr) {
        // Name-resolution checks are performed by the semantic analyzer, which
        // reports through the diagnostics system. The symbol-table pass only
        // builds the inventory and does not print warnings itself.
    }

    private void visitCallExpression(CallExpr call) {
        ASTNode function = call.getFunction();
        visitExpression(function);

        if (function instanceof IdentifierExpr) {
            String funcName = ((IdentifierExpr) function).getName();
            int lineNumber = call.getLineNumber();

            String key = funcName + "_" + lineNumber;

            if (isBuiltinName(funcName) && !processedBuiltins.contains(key)) {
                symbolTable.addBuiltinFunction(funcName, lineNumber);
                processedBuiltins.add(key);
                log("Added built-in: " + funcName + " at line " + lineNumber);
            }
        }

        List<Expression> arguments = call.getArguments();
        if (arguments != null) {
            for (Expression arg : arguments) {
                visitExpression(arg);
            }
        }
    }

    private void visitAttributeExpression(AttributeExpr attr) {
        String base = dottedBaseName(attr.getBase());
        String attribute = attr.getAttribute();

        symbolTable.addAttribute(base, attribute, attr.getLineNumber());
        log("Recorded attribute use: " + base + "." + attribute + " at line " + attr.getLineNumber());
        // The base is traversed by the generic child loop in visitExpression().
    }

    /** Builds a dotted name from an expression using AST accessors (no strings). */
    private String dottedBaseName(Expression expr) {
        if (expr instanceof IdentifierExpr id) {
            return id.getName();
        }
        if (expr instanceof AttributeExpr a) {
            return dottedBaseName(a.getBase()) + "." + a.getAttribute();
        }
        return "<expr>";
    }

    private void visitBinaryExpression(BinaryExpr expr) {
        visitExpression(expr.getLeft());
        visitExpression(expr.getRight());
    }

    private void visitCompareExpression(CompareExpr expr) {
        visitExpression(expr.getLeft());
        List<Expression> rights = expr.getRights();
        if (rights != null) {
            for (Expression right : rights) {
                visitExpression(right);
            }
        }
    }

    private void visitAssignmentChain(AssignmentChainStmt stmt) {
        List<ASTNode> children = stmt.getChildren();
        if (children.isEmpty()) return;

        int lastIndex = children.size() - 1;

        for (int i = 0; i < lastIndex; i++) {
            ASTNode target = children.get(i);
            if (target instanceof IdentifierExpr) {
                String varName = ((IdentifierExpr) target).getName();

                if (isInGlobalScope()) {
                    symbolTable.addGlobalVariable(varName, target.getLineNumber());
                } else {
                    symbolTable.addLocalVariable(varName, target.getLineNumber());
                }
            }
        }

        visitExpression(children.get(lastIndex));
    }

    private void visitClass(ClassDefStmt stmt) {
        symbolTable.enterScope(stmt.getName(), FlaskSymbolTable.Scope.ScopeType.CLASS);
        if (stmt.getBody() != null) {
            visitSuite(stmt.getBody());
        }
        symbolTable.exitScope();
    }

    private void visitIf(IfStmt stmt) {
        List<ASTNode> children = stmt.getChildren();
        if (children.size() < 2) return;

        visitExpression(children.get(0));

        symbolTable.enterScope("if_block", FlaskSymbolTable.Scope.ScopeType.IF);
        if (children.get(1) instanceof Suite) {
            visitSuite((Suite) children.get(1));
        }
        symbolTable.exitScope();
    }

    private void visitWhile(WhileStmt stmt) {
        List<ASTNode> children = stmt.getChildren();
        if (children.size() < 2) return;

        visitExpression(children.get(0));

        symbolTable.enterScope("while_loop", FlaskSymbolTable.Scope.ScopeType.LOOP);
        if (children.get(1) instanceof Suite) {
            visitSuite((Suite) children.get(1));
        }
        symbolTable.exitScope();
    }

    private void visitFor(ForStmt stmt) {
        List<ASTNode> children = stmt.getChildren();
        if (children.size() < 3) return;

        ASTNode iterator = children.get(0);
        if (iterator instanceof IdentifierExpr) {
            String varName = ((IdentifierExpr) iterator).getName();
            symbolTable.addLoopVariable(varName, iterator.getLineNumber());
        }

        visitExpression(children.get(1));

        symbolTable.enterScope("for_loop", FlaskSymbolTable.Scope.ScopeType.LOOP);
        if (children.get(2) instanceof Suite) {
            visitSuite((Suite) children.get(2));
        }
        symbolTable.exitScope();
    }

    private void visitGlobal(GlobalStmt stmt) {
        // لا شيء
    }

    private void visitReturn(ReturnStmt stmt) {
        if (!stmt.getChildren().isEmpty()) {
            visitExpression(stmt.getChildren().get(0));
        }
    }

    private void visitSuite(Suite suite) {
        visitStatements(suite.getChildren());
    }

    private void visitExpressionStmt(ExpressionStmt stmt) {
        if (!stmt.getChildren().isEmpty()) {
            visitExpression(stmt.getChildren().get(0));
        }
    }

    private boolean isInGlobalScope() {
        FlaskSymbolTable.Scope current = symbolTable.getCurrentScope();
        return current != null && current.getScopeType() == FlaskSymbolTable.Scope.ScopeType.GLOBAL;
    }

    private boolean isBuiltinName(String name) {
        String[] builtins = {
                "len", "int", "str", "list", "dict", "bool", "float",
                "print", "range", "abs", "min", "max", "sum", "sorted"
        };

        for (String builtin : builtins) {
            if (builtin.equalsIgnoreCase(name)) {
                return true;
            }
        }

        return false;
    }

    private void log(String message) {
        if (debugMode) {
            System.out.println("[DEBUG] " + message);
        }
    }
}