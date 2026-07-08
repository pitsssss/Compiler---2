package AST.literal;

import AST.ASTNode;
import AST.expr.Expression;

import java.util.ArrayList;
import java.util.List;

/**
 * A Python dictionary literal: {@code { key: value, ... }}.
 * Keys and values are kept as parallel lists of expressions.
 */
public class DictLiteralExpr extends Expression {

    private final List<Expression> keys;
    private final List<Expression> values;

    public DictLiteralExpr(List<Expression> keys, List<Expression> values, int line) {
        super("DictLiteralExpr", line);
        this.keys = keys;
        this.values = values;
    }

    public List<Expression> getKeys() { return keys; }
    public List<Expression> getValues() { return values; }
    public int size() { return keys.size(); }

    @Override
    public List<ASTNode> getChildren() {
        List<ASTNode> children = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            children.add(keys.get(i));
            children.add(values.get(i));
        }
        return children;
    }

    @Override
    public String toString() {
        return "DictLiteralExpr(size=" + keys.size() + ") (line " + lineNumber + ")";
    }
}
