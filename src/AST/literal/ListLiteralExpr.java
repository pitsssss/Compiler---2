package AST.literal;

import AST.ASTNode;
import AST.expr.Expression;

import java.util.List;

public class ListLiteralExpr extends Expression {

    private List<Expression> elements;

    public ListLiteralExpr(List<Expression> elements, int line) {
        super("ListLiteralExpr", line);
        this.elements = elements;
    }

    public List<Expression> getElements() { return elements; }

    @Override
    public List<ASTNode> getChildren() {
        return List.copyOf(elements);
    }
    @Override
    public String toString() {
        return "ListLiteralExpr(size=" + elements.size() + ") (line " + lineNumber + ")";
    }

}
