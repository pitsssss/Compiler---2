package AST;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Generic depth-first walker for any {@link ASTNode} tree. Used by tests and
 * metadata verification so traversal logic is not duplicated.
 */
public final class ASTWalker {

    private ASTWalker() {}

    /** Visits every non-null node in pre-order (node, then children). */
    public static void walkPreOrder(ASTNode root, Consumer<ASTNode> visitor) {
        if (root == null || visitor == null) return;
        visitor.accept(root);
        for (ASTNode child : safeChildren(root)) {
            walkPreOrder(child, visitor);
        }
    }

    /** Collects all reachable non-null nodes (pre-order). */
    public static List<ASTNode> collect(ASTNode root) {
        List<ASTNode> out = new ArrayList<>();
        walkPreOrder(root, out::add);
        return out;
    }

    /** True when every reachable node has a non-blank {@link ASTNode#getNodeName()}. */
    public static boolean allHaveNodeName(ASTNode root) {
        for (ASTNode n : collect(root)) {
            if (n.getNodeName() == null || n.getNodeName().isBlank()) return false;
        }
        return root != null;
    }

    /**
     * True when every non-synthetic node has line &gt; 0.
     * Synthetic roots (e.g. multi-template forest wrappers) may use line 0.
     */
    public static boolean allParsedNodesHaveLine(ASTNode root) {
        for (ASTNode n : collect(root)) {
            if (isSynthetic(n)) continue;
            if (n.getLineNumber() <= 0) return false;
        }
        return root != null;
    }

    /** Documented synthetic nodes that are allowed line = 0. */
    public static boolean isSynthetic(ASTNode node) {
        if (node == null) return true;
        String name = node.getNodeName();
        return "TemplateAstForest".equals(name) || "DataFlowDiagram".equals(name);
    }

    private static List<ASTNode> safeChildren(ASTNode node) {
        List<ASTNode> ch = node.getChildren();
        return ch == null ? List.of() : ch;
    }
}
