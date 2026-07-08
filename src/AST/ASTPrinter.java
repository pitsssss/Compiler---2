package AST;

import java.util.List;

/**
 * Hierarchical, human-readable printer for ANY AST (Python/Flask or Template).
 * It relies purely on the polymorphic {@link ASTNode#getChildren()} and
 * {@link ASTNode#toString()} contracts, so a single printer serves both trees.
 *
 * Each line shows the node name and its source line (and column when known),
 * indented to reflect the parent/child hierarchy.
 */
public final class ASTPrinter {

    private ASTPrinter() {}

    /** Prints the tree under a titled banner to stdout. */
    public static void print(String title, ASTNode root) {
        System.out.println();
        System.out.println("==================== " + title + " ====================");
        if (root == null) {
            System.out.println("(empty tree)");
        } else {
            printNode(root, "", true);
        }
        System.out.println("=".repeat(40 + title.length()));
    }

    /** Returns the tree as a String (used by tests). */
    public static String render(ASTNode root) {
        StringBuilder sb = new StringBuilder();
        if (root != null) renderNode(root, "", true, sb);
        return sb.toString();
    }

    private static void printNode(ASTNode node, String prefix, boolean isLast) {
        System.out.println(prefix + (isLast ? "└─ " : "├─ ") + node.toString());
        List<ASTNode> children = safeChildren(node);
        String childPrefix = prefix + (isLast ? "   " : "│  ");
        for (int i = 0; i < children.size(); i++) {
            ASTNode child = children.get(i);
            if (child == null) continue;
            printNode(child, childPrefix, i == children.size() - 1);
        }
    }

    private static void renderNode(ASTNode node, String prefix, boolean isLast, StringBuilder sb) {
        sb.append(prefix).append(isLast ? "└─ " : "├─ ").append(node.toString()).append('\n');
        List<ASTNode> children = safeChildren(node);
        String childPrefix = prefix + (isLast ? "   " : "│  ");
        for (int i = 0; i < children.size(); i++) {
            ASTNode child = children.get(i);
            if (child == null) continue;
            renderNode(child, childPrefix, i == children.size() - 1, sb);
        }
    }

    /** Counts every non-null node in the tree (used by tests). */
    public static int countNodes(ASTNode root) {
        if (root == null) return 0;
        int count = 1;
        for (ASTNode c : safeChildren(root)) {
            if (c != null) count += countNodes(c);
        }
        return count;
    }

    private static List<ASTNode> safeChildren(ASTNode node) {
        List<ASTNode> children = node.getChildren();
        return children == null ? List.of() : children;
    }
}
