package AST.expr;

import AST.ASTNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CallExpr extends Expression {

    private final Expression function;
    private final List<Expression> arguments;               // positional
    private final Map<String, Expression> keywordArguments; // name=value

    public CallExpr(Expression function, List<Expression> arguments, int line) {
        this(function, arguments, new LinkedHashMap<>(), line);
    }

    public CallExpr(Expression function, List<Expression> arguments,
                    Map<String, Expression> keywordArguments, int line) {
        super("CallExpr", line);
        this.function = function;
        this.arguments = arguments;
        this.keywordArguments = keywordArguments;
    }

    public Expression getFunction() { return function; }
    public List<Expression> getArguments() { return arguments; }
    public Map<String, Expression> getKeywordArguments() { return keywordArguments; }

    @Override
    public List<ASTNode> getChildren() {
        List<ASTNode> ch = new ArrayList<>();
        ch.add(function);
        ch.addAll(arguments);
        ch.addAll(keywordArguments.values());
        return ch;
    }

    @Override
    public String toString() {
        return "CallExpr (line " + lineNumber + ")";
    }
}
