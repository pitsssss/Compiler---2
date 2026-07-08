//package SymbolTable;
//
//import AST.ASTNode;
//import AST.template.jinja.*;
//import AST.template.jinja.expr.JinjaExpr;
//import AST.template.jinja.stmt.*;
//
//public class JinjaASTVisitor {
//
//    private final TemplateSymbolTable templateSymbolTable;
//    public JinjaASTVisitor(TemplateSymbolTable templateSymbolTable) {
//        this.templateSymbolTable = templateSymbolTable;
//    }
//    public TemplateSymbolTable getSymbolTable() {
//        return templateSymbolTable;
//    }
//
//    public void visit(ASTNode node) {
//
//        if (node instanceof JinjaBlockStmt block) {
//            templateSymbolTable.addBlock(block.getName(), block);
//            visit(block.getBody());
//
//        } else if (node instanceof JinjaForStmt forStmt) {
//            for (String var : forStmt.getVariables()) {
//                templateSymbolTable.addVariable(var);
//            }
//            visit(forStmt.getIterable());
//            // بعد زيارة الجسم:
//            for (ASTNode child : forStmt.getBody().getChildren()) {
//                visit(child);
//                if (child instanceof JinjaExpr expr) {
//                    for (String v : expr.getVariables()) {
//                        templateSymbolTable.addVariable(v);
//                    }
//                }
//            }
//        }
//
//        else if (node instanceof JinjaIfStmt ifStmt) {
//            for (String v : ifStmt.getCondition().getVariables()) {
//                templateSymbolTable.addVariable(v);
//            }
//            visit(ifStmt.getThenBody());
//            for (JinjaElifClause elif : ifStmt.getElifClauses()) {
//                visit(elif);
//                for (String v : elif.getCondition().getVariables()) {
//                    templateSymbolTable.addVariable(v);
//                }
//            }
//            if (ifStmt.getElseBody() != null) visit(ifStmt.getElseBody());
//        }
//        else if (node instanceof JinjaElifClause elif) {
//            visit(elif.getCondition());
//            visit(elif.getBody());
//
//        } else if (node instanceof JinjaIncludeStmt include) {
//            templateSymbolTable.addInclude(include.getTemplateName());
//
//        } else if (node instanceof JinjaExtendsStmt extendsStmt) {
//            templateSymbolTable.setExtendsTemplate(extendsStmt.getTemplateName());
//
//        } else if (node instanceof JinjaBody body) {
//            for (ASTNode child : body.getBodyChildren()) {
//                visit(child);
//            }
//
//        } else if (node instanceof JinjaExpr expr) {
//            for (String var : expr.getVariables()) {
//                templateSymbolTable.addVariable(var);
//            }
//
//
//    }else {
//            for (ASTNode child : node.getChildren()) {
//                visit(child);
//            }
//        }
//
//
//        System.out.println("Visiting: " + node.getClass().getSimpleName());
//
//    }
//}

package SymbolTable;

import AST.ASTNode;
import AST.template.jinja.*;
import AST.template.jinja.expr.*;
import AST.template.jinja.stmt.*;

public class JinjaASTVisitor {

    private final TemplateSymbolTable table;

    public JinjaASTVisitor(TemplateSymbolTable table) {
        this.table = table;
    }

    public void visit(ASTNode node) {
        if (node == null) return;

        if (node instanceof JinjaBlockStmt block) {
            table.addBlock(block.getName(), block);
            visit(block.getBody());
            return;
        }


        if (node instanceof JinjaForStmt forStmt) {
            for (String var : forStmt.getVariables()) {
                table.addLoopVariable(var);
            }
            visit(forStmt.getIterable());
            visit(forStmt.getBody());
            return;
        }


        if (node instanceof JinjaIfStmt ifStmt) {
            extractExpr(ifStmt.getCondition());
            visit(ifStmt.getThenBody());
            for (JinjaElifClause elif : ifStmt.getElifClauses()) {
                extractExpr(elif.getCondition());
                visit(elif.getBody());
            }
            if (ifStmt.getElseBody() != null) {
                visit(ifStmt.getElseBody());
            }
            return;
        }


        if (node instanceof JinjaIncludeStmt inc) {
            table.addInclude(inc.getTemplateName());
            return;
        }

        if (node instanceof JinjaExtendsStmt ext) {
            table.setExtendsTemplate(ext.getTemplateName());
            return;
        }


        if (node instanceof JinjaBody body) {
            for (ASTNode child : body.getBodyChildren()) {
                visit(child);
            }
            return;
        }


        if (node instanceof JinjaExpr expr) {
            extractExpr(expr);
            return;
        }


        for (ASTNode child : node.getChildren()) {
            visit(child);
        }
    }



    private void extractExpr(JinjaExpr expr) {
        if (expr == null) return;


        for (String v : expr.getVariables()) {
            table.addGlobalVariable(v);
        }

        if (expr instanceof JinjaCallExpr call) {
            if (call.getCallee() instanceof JinjaIdentifierExpr id) {
                table.addFunction(id.getName());
            }
        }

        if (expr instanceof JinjaFilterExpr filter) {
            table.addFilter(filter.getFilterName());
            extractExpr(filter.getBase());
        }

        if (expr instanceof JinjaAttrExpr attr) {
            table.addAttribute(attr.toString());
        }

        for (ASTNode child : expr.getChildren()) {
            if (child instanceof JinjaExpr je) {
                extractExpr(je);
            }
        }
    }
}

