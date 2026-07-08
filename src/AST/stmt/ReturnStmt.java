package AST.stmt;

import AST.ASTNode;
import AST.expr.Expression;

import java.util.List;

public class ReturnStmt extends Statement {

    private Expression value;

    public ReturnStmt(Expression value, int lineNumber) {
        super("ReturnStmt", lineNumber);
        this.value = value;
    }

    public Expression getValue() { return value; }

    @Override
    public List<ASTNode> getChildren() {
        return List.of(value);
    }
    @Override
    public String toString() {
        return "ReturnStmt (line " + lineNumber + ")";
    }

}
