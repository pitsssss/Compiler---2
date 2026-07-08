package AST;

import java.util.List;

/**
 * Common base class for every node in both the Python/Flask AST and the
 * Template (HTML/CSS/Jinja2) AST.
 *
 * Encapsulation: fields are protected with public accessors.
 * Inheritance:   every concrete node extends this class (directly or through
 *                the intermediate abstract categories Expression/Statement/etc.).
 * Polymorphism:  {@link #getChildren()} and {@link #toString()} are overridden
 *                by subclasses and consumed uniformly by printers/analyzers.
 */
public abstract class ASTNode {
    protected String nodeName;
    protected int lineNumber;
    /** Column (0-based, from ANTLR) when available; -1 when unknown. */
    protected int columnNumber = -1;

    public ASTNode(String nodeName, int lineNumber) {
        this.nodeName = nodeName;
        this.lineNumber = lineNumber;
    }

    public String getNodeName() {
        return nodeName;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public int getColumnNumber() {
        return columnNumber;
    }

    /** Fluent column setter so visitors can attach a column right after construction. */
    public ASTNode setColumn(int column) {
        this.columnNumber = column;
        return this;
    }

    /** Every node exposes its children uniformly; leaf nodes return an empty list. */
    public List<ASTNode> getChildren() {
        return List.of();
    }

    @Override
    public String toString() {
        if (columnNumber >= 0) {
            return nodeName + " (line " + lineNumber + ", col " + columnNumber + ")";
        }
        return nodeName + " (line " + lineNumber + ")";
    }
}
