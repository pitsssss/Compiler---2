package AST.literal;

public class NumberLiteralExpr extends LiteralExpr {

    private String value;

    public NumberLiteralExpr(String value, int lineNumber) {
        super("NumberLiteralExpr", lineNumber);
        this.value = value;
    }

    public String getValue() { return value; }
    @Override
    public String toString() {
        return "NumberLiteralExpr(" + value + ") (line " + lineNumber + ")";
    }

}
