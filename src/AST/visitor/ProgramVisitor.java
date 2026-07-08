package AST.visitor;

import AST.ASTNode;
import AST.Program;
import AST.stmt.Statement;
import antlr.FlaskParser;
import antlr.FlaskParserBaseVisitor;

import java.util.ArrayList;
import java.util.List;

public class ProgramVisitor extends FlaskParserBaseVisitor<Program> {

    @Override
    public Program visitProg(FlaskParser.ProgContext ctx) {

        FlaskVisitor flaskVisitor = new FlaskVisitor();
        List<Statement> statements = new ArrayList<>();

        for (FlaskParser.StmtContext stmtCtx : ctx.stmt()) {
            ASTNode node = flaskVisitor.visit(stmtCtx);

            if (node instanceof Statement) {
                statements.add((Statement) node);
            }
        }

        return new Program(statements, ctx.start.getLine());
    }
}

