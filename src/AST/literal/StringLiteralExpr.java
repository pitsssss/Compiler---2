package AST.literal;

public class StringLiteralExpr extends LiteralExpr {

    private String value;

    public StringLiteralExpr(String value, int lineNumber) {
        super("StringLiteralExpr", lineNumber);
        this.value = value;
    }

    /** Raw literal text as it appeared in source (still quoted). */
    public String getValue() { return value; }

    /** Literal text with the surrounding quotes stripped. */
    public String getUnquoted() {
        if (value == null || value.length() < 2) return value;
        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '"' || first == '\'') && first == last) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
    @Override
    public String toString() {
        return "StringLiteralExpr(\"" + value + "\") (line " + lineNumber + ")";
    }

}
