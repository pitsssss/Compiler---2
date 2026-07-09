package AST;

import java.io.FileWriter;
import java.io.IOException;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class ASTGraphvizPrinter {

    private static int nodeCounter = 0;
    private static final Map<ASTNode, String> ids = new IdentityHashMap<>();

    private static String getId(ASTNode node) {
        return ids.computeIfAbsent(node, n -> "node" + (++nodeCounter));
    }

    public static void print(ASTNode root, String fileName) throws IOException {
        nodeCounter = 0;
        ids.clear();

        try (FileWriter writer = new FileWriter(fileName)) {
            writer.write("digraph AST {\n");
            writer.write("  node [shape=box, fontname=\"Arial\"];\n");
            traverse(root, writer);
            writer.write("}\n");
        }
    }

    /** Exports multiple labeled template roots inside one DOT file. */
    public static void printTemplateForest(Map<String, ASTNode> templates, String fileName)
            throws IOException {
        TemplateAstForest forest = new TemplateAstForest(templates);
        print(forest, fileName);
    }

    private static void traverse(ASTNode node, FileWriter writer) throws IOException {
        if (node == null) return;

        String id = getId(node);
        writer.write(String.format(
                "  %s [label=\"%s\"];\n",
                id,
                escape(labelFor(node))
        ));

        List<ASTNode> children = node.getChildren();
        for (ASTNode child : children) {
            if (child == null) continue;
            String childId = getId(child);
            writer.write(String.format("  %s -> %s;\n", id, childId));
            traverse(child, writer);
        }
    }

    /** Rich label: node type, summary, line, column. */
    static String labelFor(ASTNode node) {
        StringBuilder sb = new StringBuilder();
        sb.append(node.getNodeName());
        String summary = node.toString();
        if (summary != null && !summary.startsWith(node.getNodeName())) {
            sb.append("\\n").append(summary.replace("\"", "'"));
        }
        if (node.getLineNumber() > 0) {
            sb.append("\\nline ").append(node.getLineNumber());
            if (node.getColumnNumber() >= 0) {
                sb.append(", col ").append(node.getColumnNumber());
            }
        }
        return sb.toString();
    }

    private static String escape(String text) {
        return text.replace("\"", "\\\"");
    }
}
