package model;

import AST.expr.*;
import AST.literal.*;
import AST.stmt.AssignmentStmt;

/** Best-effort static type inference from Python AST nodes (no regex). */
public final class TypeInference {

    private TypeInference() {}

    public static ValueType infer(Expression expr, AppModel model) {
        if (expr == null) return ValueType.UNKNOWN;
        if (expr instanceof NoneLiteralExpr) return ValueType.NONE;
        if (expr instanceof BooleanLiteralExpr) return ValueType.BOOLEAN;
        if (expr instanceof StringLiteralExpr) return ValueType.STRING;
        if (expr instanceof NumberLiteralExpr) return ValueType.NUMBER;
        if (expr instanceof ListLiteralExpr) return ValueType.LIST;
        if (expr instanceof DictLiteralExpr) return ValueType.DICT;
        if (expr instanceof IdentifierExpr id) {
            if (id.getName().equals(model.getProductsVariableName())) return ValueType.LIST;
            if (model.hasGlobal(id.getName())) {
                // globals assigned in module — re-walk would be expensive; use product rows hint
                if (id.getName().equals(model.getProductsVariableName())) return ValueType.LIST;
            }
        }
        if (expr instanceof IndexExpr ix) {
            Expression base = firstExpr(ix);
            if (base instanceof IdentifierExpr id
                    && id.getName().equals(model.getProductsVariableName())) {
                return ValueType.DICT;
            }
        }
        return ValueType.UNKNOWN;
    }

    public static ValueType inferGlobal(String name, AppModel model) {
        if (name.equals(model.getProductsVariableName())) return ValueType.LIST;
        return ValueType.UNKNOWN;
    }

    /** Infers type of a render_template keyword argument by its RHS expression. */
    public static ValueType inferContextValue(String contextName, Expression rhs, AppModel model) {
        ValueType t = infer(rhs, model);
        if (t != ValueType.UNKNOWN) return t;
        if (contextName.equals("product") && rhs instanceof IndexExpr) return ValueType.DICT;
        if (contextName.equals("products")) return ValueType.LIST;
        if (contextName.equals("errors")) return ValueType.LIST;
        return ValueType.UNKNOWN;
    }

    /** Maps a Jinja attribute access to the expected base type (e.g. {@code .upper} -> STRING). */
    public static ValueType requiredBaseTypeForAttribute(String attribute) {
        return switch (attribute) {
            case "upper", "lower", "strip", "title", "capitalize", "split", "replace" -> ValueType.STRING;
            case "append", "extend", "pop", "remove" -> ValueType.LIST;
            case "keys", "values", "items", "get" -> ValueType.DICT;
            default -> ValueType.UNKNOWN;
        };
    }

    private static Expression firstExpr(IndexExpr ix) {
        for (var c : ix.getChildren()) {
            if (c instanceof Expression e) return e;
        }
        return null;
    }
}
