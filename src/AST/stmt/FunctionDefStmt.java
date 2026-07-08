package AST.stmt;

import AST.ASTNode;
import AST.suite.Suite;
import java.util.List;

public class FunctionDefStmt extends Statement {

    private String name;
    private List<String> parameters;
    private Suite body;

    public FunctionDefStmt(String name, List<String> parameters,
                           Suite body, int lineNumber) {
        super("FunctionDefStmt", lineNumber);
        this.name = name;
        this.parameters = parameters;
        this.body = body;
    }
    public String getName() {
        return name;
    }

    public List<String> getParameters() {
        return parameters;
    }

    public Suite getBody() {
        return body;
    }


    @Override
    public List<ASTNode> getChildren() {
        return List.of(body);
    }

    @Override
    public String toString() {
        return "FunctionDefStmt(" + name + ") (line " + lineNumber + ")";
    }


}
