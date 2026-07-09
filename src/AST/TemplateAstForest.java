package AST;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Synthetic wrapper used only for exporting multiple template AST roots into one
 * Graphviz file. Not produced by the parser; line 0 marks it as synthetic.
 */
public final class TemplateAstForest extends ASTNode {

    private final Map<String, ASTNode> templates;

    public TemplateAstForest(Map<String, ASTNode> templates) {
        super("TemplateAstForest", 0);
        this.templates = new LinkedHashMap<>(templates);
    }

    public Map<String, ASTNode> getTemplates() {
        return Map.copyOf(templates);
    }

    @Override
    public List<ASTNode> getChildren() {
        return new ArrayList<>(templates.values());
    }

    @Override
    public String toString() {
        return "TemplateAstForest (" + templates.size() + " templates)";
    }
}
