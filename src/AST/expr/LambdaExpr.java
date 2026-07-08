package AST.expr;

import AST.ASTNode;
import java.util.List;

public class LambdaExpr extends Expression {

    private List<String> params;
    private Expression body;

    public LambdaExpr(List<String> params, Expression body, int line) {
        super("LambdaExpr", line);
        this.params = params;
        this.body = body;
    }

    public List<String> getParams() { return params; }
    public Expression getBody() { return body; }

    @Override
    public List<ASTNode> getChildren() {
        return List.of(body);
    }
    @Override
    public String toString() {
        return "LambdaExpr(params=" + params + ") (line " + lineNumber + ")";
    }

}
