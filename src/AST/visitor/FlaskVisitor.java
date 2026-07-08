package AST.visitor;

import AST.ASTNode;
import AST.Program;
import AST.expr.*;
import AST.literal.*;
import AST.stmt.*;
import AST.suite.*;
import antlr.FlaskParser;
import antlr.FlaskParserBaseVisitor;

import java.util.ArrayList;
import java.util.List;

public class FlaskVisitor extends FlaskParserBaseVisitor<ASTNode> {

    @Override
    public ASTNode visitProg(FlaskParser.ProgContext ctx) {
        List<Statement> list = new ArrayList<>();
        for (var s : ctx.stmt()) {
            Statement st = (Statement) visit(s);
            if (st != null) list.add(st);
        }
        return new Program(list, 1);
    }

    /* =========================
       STATEMENTS
     ========================= */

    @Override
    public ASTNode visitSimpleStmtLine(FlaskParser.SimpleStmtLineContext ctx) {

        ASTNode inner = visit(ctx.simple_stmt());

        if (inner instanceof Expression) {
            return new ExpressionStmt((Expression) inner, ctx.start.getLine());
        }

        return inner;
    }


    @Override
    public ASTNode visitCompoundStmt(FlaskParser.CompoundStmtContext ctx) {
        return visit(ctx.getChild(0));  // def, if, while, for...
    }

    @Override
    public ASTNode visitEmptyLine(FlaskParser.EmptyLineContext ctx) {
        return null;
    }

    @Override
    public ASTNode visitAssign_target(FlaskParser.Assign_targetContext ctx) {
        int line = ctx.start.getLine();

        // Base: the first IDENTIFIER token
        Expression expr = new IdentifierExpr(ctx.IDENTIFIER(0).getText(), line);

        // Walk the children: IDENTIFIER (DOT IDENTIFIER | LBRACK expr RBRACK)*
        // child(0) = IDENTIFIER, so start from 1
        int i = 1;
        while (i < ctx.getChildCount()) {

            org.antlr.v4.runtime.tree.ParseTree child = ctx.getChild(i);
            String text = child.getText();

            // Case 1: .name
            if (".".equals(text)) {
                org.antlr.v4.runtime.tree.ParseTree nameNode = ctx.getChild(i + 1);
                String attrName = nameNode.getText();
                expr = new AttributeExpr(expr, attrName, line);
                i += 2; // skip '.' and name
            }
            // Case 2: [ expr ]
            else if ("[".equals(text)) {
                FlaskParser.ExprContext ectx =
                        (FlaskParser.ExprContext) ctx.getChild(i + 1);
                Expression indexExpr = (Expression) visit(ectx);
                expr = new IndexExpr(expr, indexExpr, line);
                i += 3; // skip '[', expr, ']'
            } else {
                i++;
            }
        }

        return expr;
    }



    @Override
    public ASTNode visitAssignment(FlaskParser.AssignmentContext ctx) {

        // Handle simple x = expr
        if (ctx.assign_target().size() == 1)
        {
            Expression lhs = (Expression) visit(ctx.assign_target(0));
            Expression rhs = (Expression) visit(ctx.expr());
            return new AssignmentStmt(lhs, rhs, ctx.start.getLine());
        }

        // Otherwise: chained assignment a = b = c = value
        List<FlaskParser.Assign_targetContext> targets = ctx.assign_target();

        List<Expression> lhsList = new ArrayList<>();
        for (var t : targets)
            lhsList.add((Expression) visit(t));

        Expression rhs = (Expression) visit(ctx.expr());

        return new AssignmentChainStmt(lhsList, rhs, ctx.start.getLine());
    }


    @Override
    public ASTNode visitReturn_stmt(FlaskParser.Return_stmtContext ctx) {
        return new ReturnStmt((Expression) visit(ctx.expr()), ctx.start.getLine());
    }


    @Override public ASTNode visitPass_stmt(FlaskParser.Pass_stmtContext ctx)
    { return new PassStmt(ctx.start.getLine()); }

    @Override public ASTNode visitBreak_stmt(FlaskParser.Break_stmtContext ctx)
    { return new BreakStmt(ctx.start.getLine()); }

    @Override public ASTNode visitContinue_stmt(FlaskParser.Continue_stmtContext ctx)
    { return new ContinueStmt(ctx.start.getLine()); }

    @Override
    public ASTNode visitDel_stmt(FlaskParser.Del_stmtContext ctx) {

        List<Expression> list = new ArrayList<>();

        for (var e : ctx.expr())
            list.add((Expression) visit(e));

        return new DelStmt(list, ctx.start.getLine());
    }

    @Override
    public ASTNode visitGlobal_stmt(FlaskParser.Global_stmtContext ctx) {

        List<String> names =
                ctx.IDENTIFIER().stream().map(t -> t.getText()).toList();

        return new GlobalStmt(names, ctx.start.getLine());
    }

    @Override
    public ASTNode visitFromImport(FlaskParser.FromImportContext ctx) {

        String module = ctx.dotted_name().getText();

        List<String> names =
                ctx.IDENTIFIER().stream().map(id -> id.getText()).toList();

        return new FromImportStmt(module, names, ctx.start.getLine());
    }

    @Override
    public ASTNode visitIf_stmt(FlaskParser.If_stmtContext ctx) {

        Expression cond = (Expression) visit(ctx.expr(0));
        Suite thenSuite = (Suite) visit(ctx.suite(0));

        List<Expression> elifConds = new ArrayList<>();
        List<Suite> elifSuites = new ArrayList<>();

        int elifCount = ctx.expr().size() - 1;

        for (int i = 1; i <= elifCount; i++) {
            elifConds.add((Expression) visit(ctx.expr(i)));
            elifSuites.add((Suite) visit(ctx.suite(i)));
        }

        Suite elseSuite = null;
        if (ctx.ELSE() != null) {
            elseSuite = (Suite) visit(ctx.suite(ctx.suite().size() - 1));
        }

        return new IfStmt(cond, thenSuite, elifConds, elifSuites, elseSuite,
                ctx.start.getLine());
    }

    @Override
    public ASTNode visitWhile_stmt(FlaskParser.While_stmtContext ctx) {
        Expression cond = (Expression) visit(ctx.expr());
        Suite body = (Suite) visit(ctx.suite());
        return new WhileStmt(cond, body, ctx.start.getLine());
    }

    @Override
    public ASTNode visitFor_stmt(FlaskParser.For_stmtContext ctx) {

        IdentifierExpr iterator =
                new IdentifierExpr(ctx.IDENTIFIER().getText(), ctx.start.getLine());

        Expression iterable = (Expression) visit(ctx.expr());
        Suite body = (Suite) visit(ctx.suite());

        return new ForStmt(iterator, iterable, body, ctx.start.getLine());
    }

    @Override
    public ASTNode visitFunc_def(FlaskParser.Func_defContext ctx) {

        String name = ctx.IDENTIFIER().getText();

        List<String> params = new ArrayList<>();
        if (ctx.parameters() != null) {
            ctx.parameters().IDENTIFIER().forEach(id -> params.add(id.getText()));
        }

        Suite body = (Suite) visit(ctx.suite());

        FunctionDefStmt fn = new FunctionDefStmt(name, params, body, ctx.start.getLine());
        fn.setColumn(ctx.IDENTIFIER().getSymbol().getCharPositionInLine());
        return fn;
    }


    @Override
    public ASTNode visitClass_def(FlaskParser.Class_defContext ctx) {

        String name = ctx.IDENTIFIER(0).getText();

        String parent = null;
        if (ctx.IDENTIFIER().size() > 1) {
            parent = ctx.IDENTIFIER(1).getText();
        }

        Suite body = (Suite) visit(ctx.suite());

        return new ClassDefStmt(name, parent, body, ctx.start.getLine());
    }

    @Override
    public ASTNode visitDecorator(FlaskParser.DecoratorContext ctx) {

        Expression expr = (Expression) visit(ctx.primary());
        return new Decorator(expr, ctx.start.getLine());
    }

    @Override
    public ASTNode visitDecorated(FlaskParser.DecoratedContext ctx) {

        List<Decorator> list = new ArrayList<>();
        for (FlaskParser.DecoratorContext d : ctx.decorator()) {
            list.add((Decorator) visit(d));
        }

        Statement target = (Statement) visit(ctx.func_def() != null
                ? ctx.func_def()
                : ctx.class_def());

        return new DecoratedStmt(list, target, ctx.start.getLine());
    }


    /* =========================
       SUITES
     ========================= */

    @Override
    public ASTNode visitInlineSuite(FlaskParser.InlineSuiteContext ctx) {
        Statement st = (Statement) visit(ctx.simple_stmt());
        return new InlineSuite(st, ctx.start.getLine());
    }


    @Override
    public ASTNode visitBlockSuite(FlaskParser.BlockSuiteContext ctx) {
        List<Statement> list = new ArrayList<>();
        for (FlaskParser.StmtContext s : ctx.stmt()) {
            list.add((Statement) visit(s));
        }
        return new BlockSuite(list, ctx.start.getLine());
    }


    /* =========================
       EXPRESSIONS
     ========================= */

    @Override
    public ASTNode visitExprRoot(FlaskParser.ExprRootContext ctx) {
        return visit(ctx.lambda_expr());
    }

    @Override
    public ASTNode visitLambdaExpr(FlaskParser.LambdaExprContext ctx) {

        List<String> params = new ArrayList<>();

        if (ctx.lambda_params() != null) {
            for (var id : ctx.lambda_params().IDENTIFIER()) {
                params.add(id.getText());
            }
        }

        Expression body = (Expression) visit(ctx.expr());

        return new LambdaExpr(params, body, ctx.start.getLine());
    }

    @Override
    public ASTNode visitToOrExpr(FlaskParser.ToOrExprContext ctx) {
        return visit(ctx.or_expr());
    }

    @Override
    public ASTNode visitOrExpr(FlaskParser.OrExprContext ctx) {
        Expression result = (Expression) visit(ctx.and_expr(0));

        for (int i = 1; i < ctx.and_expr().size(); i++) {
            Expression rhs = (Expression) visit(ctx.and_expr(i));
            result = new BinaryExpr(result, "or", rhs, ctx.start.getLine());
        }

        return result;
    }

    @Override
    public ASTNode visitAndExpr(FlaskParser.AndExprContext ctx) {
        Expression result = (Expression) visit(ctx.not_expr(0));

        for (int i = 1; i < ctx.not_expr().size(); i++) {
            Expression rhs = (Expression) visit(ctx.not_expr(i));
            result = new BinaryExpr(result, "and", rhs, ctx.start.getLine());
        }

        return result;
    }

    @Override
    public ASTNode visitNotExpr(FlaskParser.NotExprContext ctx) {
        Expression sub = (Expression) visit(ctx.not_expr());
        return new NotExpr(sub, ctx.start.getLine());
    }

    @Override
    public ASTNode visitCompareExpr(FlaskParser.CompareExprContext ctx) {

        if (ctx.comp_tail().isEmpty()) {
            return visit(ctx.arith_expr());
        }

        Expression left = (Expression) visit(ctx.arith_expr());

        List<String> ops = new ArrayList<>();
        List<Expression> rights = new ArrayList<>();

        for (FlaskParser.Comp_tailContext t : ctx.comp_tail()) {

            if (t instanceof FlaskParser.RelOpContext rel) {
                ops.add(rel.getChild(0).getText());
                rights.add((Expression) visit(rel.arith_expr()));
            }
            else if (t instanceof FlaskParser.IsOpContext isOp) {
                String op = (isOp.NOT() != null) ? "is not" : "is";
                ops.add(op);
                rights.add((Expression) visit(isOp.arith_expr()));
            }
        }

        return new CompareExpr(left, ops, rights, ctx.start.getLine());
    }


    @Override
    public ASTNode visitAddSubExpr(FlaskParser.AddSubExprContext ctx) {

        Expression result = (Expression) visit(ctx.term(0));

        for (int i = 1; i < ctx.term().size(); i++) {
            String op = ctx.getChild(2 * i - 1).getText();
            Expression rhs = (Expression) visit(ctx.term(i));
            result = new BinaryExpr(result, op, rhs, ctx.start.getLine());
        }

        return result;
    }


    @Override
    public ASTNode visitMulDivExpr(FlaskParser.MulDivExprContext ctx) {

        Expression result = (Expression) visit(ctx.power(0));

        for (int i = 1; i < ctx.power().size(); i++) {
            String op = ctx.getChild(2 * i - 1).getText();
            Expression rhs = (Expression) visit(ctx.power(i));
            result = new BinaryExpr(result, op, rhs, ctx.start.getLine());
        }

        return result;
    }

    @Override
    public ASTNode visitPowExpr(FlaskParser.PowExprContext ctx) {

        Expression base = (Expression) visit(ctx.unary());

        if (ctx.power() != null) {
            Expression exponent = (Expression) visit(ctx.power());
            return new BinaryExpr(base, "**", exponent, ctx.start.getLine());
        }

        return base;
    }

    @Override
    public ASTNode visitUnaryPMExpr(FlaskParser.UnaryPMExprContext ctx) {

        String op = ctx.getChild(0).getText();
        Expression expr = (Expression) visit(ctx.getChild(1));

        return new UnaryExpr(op, expr, ctx.start.getLine());
    }

    @Override
    public ASTNode visitPrimaryExpr(FlaskParser.PrimaryExprContext ctx) {
        return visit(ctx.primary());
    }


    @Override
    public ASTNode visitPrimaryRoot(FlaskParser.PrimaryRootContext ctx) {

        int line = ctx.start.getLine();

        // 1. Start with atom (base)
        Expression base = (Expression) visit(ctx.atom());

        // 2. Apply all trailers in sequence
        for (FlaskParser.TrailerContext t : ctx.trailer()) {

            if (t instanceof FlaskParser.CallTrailerContext call) {

                List<Expression> args = new ArrayList<>();
                java.util.LinkedHashMap<String, Expression> kwargs = new java.util.LinkedHashMap<>();

                if (call.arguments() != null) {
                    for (FlaskParser.ArgumentContext a : call.arguments().argument()) {
                        if (a instanceof FlaskParser.KeywordArgContext kw) {
                            kwargs.put(kw.IDENTIFIER().getText(), (Expression) visit(kw.expr()));
                        } else if (a instanceof FlaskParser.PositionalArgContext pos) {
                            args.add((Expression) visit(pos.expr()));
                        }
                    }
                }

                base = new CallExpr(base, args, kwargs, line);
            }
            else if (t instanceof FlaskParser.AttrTrailerContext attr) {

                String name = attr.IDENTIFIER().getText();
                base = new AttributeExpr(base, name, line);
            }
            else if (t instanceof FlaskParser.IndexTrailerContext idx) {

                Expression indexExpr = (Expression) visit(idx.expr());
                base = new IndexExpr(base, indexExpr, line);
            }
        }

        return base;
    }

    /* =========================
       ATOMS & LITERALS
     ========================= */

    @Override
    public ASTNode visitLiteralAtom(FlaskParser.LiteralAtomContext ctx) {
        return visit(ctx.literal());
    }


    @Override
    public ASTNode visitIdAtom(FlaskParser.IdAtomContext ctx) {
        return new IdentifierExpr(ctx.IDENTIFIER().getText(), ctx.start.getLine())
                .setColumn(ctx.start.getCharPositionInLine());
    }

    @Override
    public ASTNode visitIntNumber(FlaskParser.IntNumberContext ctx) {
        return new NumberLiteralExpr(ctx.getText(), ctx.start.getLine());
    }

    @Override
    public ASTNode visitFloatNumber(FlaskParser.FloatNumberContext ctx) {
        return new NumberLiteralExpr(ctx.getText(), ctx.start.getLine());
    }

    @Override
    public ASTNode visitSciNumber(FlaskParser.SciNumberContext ctx) {
        return new NumberLiteralExpr(ctx.getText(), ctx.start.getLine());
    }

    @Override
    public ASTNode visitTripleString(FlaskParser.TripleStringContext ctx) {
        return new StringLiteralExpr(ctx.getText(), ctx.start.getLine());
    }

    @Override
    public ASTNode visitSingleString(FlaskParser.SingleStringContext ctx) {
        return new StringLiteralExpr(ctx.getText(), ctx.start.getLine());
    }

    @Override
    public ASTNode visitDoubleString(FlaskParser.DoubleStringContext ctx) {
        return new StringLiteralExpr(ctx.getText(), ctx.start.getLine());
    }

    @Override
    public ASTNode visitTrueLiteral(FlaskParser.TrueLiteralContext ctx) {
        return new BooleanLiteralExpr(true, ctx.start.getLine());
    }

    @Override
    public ASTNode visitFalseLiteral(FlaskParser.FalseLiteralContext ctx) {
        return new BooleanLiteralExpr(false, ctx.start.getLine());
    }

    @Override
    public ASTNode visitNoneLiteral(FlaskParser.NoneLiteralContext ctx) {
        return new NoneLiteralExpr(ctx.start.getLine());
    }

    @Override
    public ASTNode visitSet_literal(FlaskParser.Set_literalContext ctx)
    {

        List<Expression> elems = new ArrayList<>();

        for (FlaskParser.ExprContext e : ctx.expr()) {
            elems.add((Expression) visit(e));
        }

        return new SetLiteralExpr(elems, ctx.start.getLine());
    }

    @Override
    public ASTNode visitList_literal(FlaskParser.List_literalContext ctx)
    {

        List<Expression> elems = new ArrayList<>();

        for (FlaskParser.ExprContext e : ctx.expr()) {
            elems.add((Expression) visit(e));
        }

        return new ListLiteralExpr(elems, ctx.start.getLine());
    }

    @Override
    public ASTNode visitDict_literal(FlaskParser.Dict_literalContext ctx)
    {
        List<Expression> keys = new ArrayList<>();
        List<Expression> values = new ArrayList<>();

        for (FlaskParser.Dict_entryContext entry : ctx.dict_entry()) {
            keys.add((Expression) visit(entry.expr(0)));
            values.add((Expression) visit(entry.expr(1)));
        }

        return new DictLiteralExpr(keys, values, ctx.start.getLine());
    }
}
