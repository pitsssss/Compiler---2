package AST.stmt;

import AST.ASTNode;
import AST.expr.Expression;

import java.util.List;

public class ExpressionStmt extends Statement {

    private Expression expression;

    public ExpressionStmt(Expression expression, int lineNumber) {
        super("ExpressionStmt", lineNumber);
        this.expression = expression;
    }

    public Expression getExpression() { return expression; }

    @Override
    public List<ASTNode> getChildren() {
        return List.of(expression);
    }

    @Override
    public String toString() {
        return "ExpressionStmt (line " + lineNumber + ")";
    }

}
