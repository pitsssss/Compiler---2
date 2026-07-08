package AST.stmt;

import AST.ASTNode;
import AST.expr.Expression;
import java.util.List;

public class Decorator extends ASTNode {

    private Expression expr;

    public Decorator(Expression expr, int line) {
        super("Decorator", line);
        this.expr = expr;
    }

    public Expression getExpr() { return expr; }

    @Override
    public List<ASTNode> getChildren() {
        return List.of(expr);
    }
    @Override
    public String toString() {
        return "Decorator (line " + lineNumber + ")";
    }

}
