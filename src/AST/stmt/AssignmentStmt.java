package AST.stmt;

import AST.ASTNode;
import AST.expr.Expression;
import java.util.List;

public class AssignmentStmt extends Statement {

    private final Expression target;
    private final Expression value;


    public AssignmentStmt(Expression target, Expression value, int lineNumber) {
        super("AssignmentStmt", lineNumber);
        this.target = target;
        this.value = value;
    }

    public Expression getTarget() { return target; }
    public Expression getValue() { return value; }

    @Override
    public List<ASTNode> getChildren() {
        return List.of(target, value);
    }
    @Override
    public String toString() {
        return "AssignmentStmt (line " + lineNumber + ")";
    }

}
