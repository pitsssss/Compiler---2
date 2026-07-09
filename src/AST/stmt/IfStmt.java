package AST.stmt;

import AST.ASTNode;
import AST.expr.Expression;
import AST.suite.Suite;

import java.util.ArrayList;
import java.util.List;

public class IfStmt extends Statement {

    private Expression condition;
    private Suite thenSuite;
    private List<Expression> elifConditions;
    private List<Suite> elifSuites;
    private Suite elseSuite;

    public IfStmt(Expression condition, Suite thenSuite,
                  List<Expression> elifConditions,
                  List<Suite> elifSuites,
                  Suite elseSuite,
                  int lineNumber) {

        super("IfStmt", lineNumber);
        this.condition = condition;
        this.thenSuite = thenSuite;
        this.elifConditions = elifConditions;
        this.elifSuites = elifSuites;
        this.elseSuite = elseSuite;
    }

    @Override
    public List<ASTNode> getChildren() {
        List<ASTNode> children = new ArrayList<>();

        children.add(condition);
        children.add(thenSuite);

        for (int i = 0; i < elifConditions.size(); i++) {
            children.add(elifConditions.get(i));
            children.add(elifSuites.get(i));
        }

        if (elseSuite != null) {
            children.add(elseSuite);
        }

        return children;
    }

    @Override
    public String toString() {
        return "IfStmt (line " + lineNumber + ")";
    }

    public Expression getCondition() { return condition; }
    public Suite getThenSuite() { return thenSuite; }
    public List<Expression> getElifConditions() { return elifConditions; }
    public List<Suite> getElifSuites() { return elifSuites; }
    public Suite getElseSuite() { return elseSuite; }

}
