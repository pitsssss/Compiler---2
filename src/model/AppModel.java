package model;

import AST.stmt.FunctionDefStmt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A semantic snapshot of the Flask program, extracted from the Python AST.
 * This is the bridge that lets the template analyzers and the generator reason
 * about the backend WITHOUT re-parsing text: routes, render calls, url_for
 * references, global data and the extracted product records all live here.
 */
public final class AppModel {

    private final Set<String> imports = new LinkedHashSet<>();
    private final Map<String, Integer> globalVars = new LinkedHashMap<>();
    private final Map<String, FunctionDefStmt> functions = new LinkedHashMap<>();
    private final List<RouteInfo> routes = new ArrayList<>();
    private final List<RenderCall> renderCalls = new ArrayList<>();
    private final List<UrlForRef> urlForRefs = new ArrayList<>();

    // Product records extracted from the `products` global literal.
    private final List<Map<String, Object>> productRows = new ArrayList<>();
    // Canonical field order used by the generator when emitting the app.
    private final List<String> productFields = new ArrayList<>();

    private String appVariableName = "app";
    private String productsVariableName = "products";

    public Set<String> getImports() { return imports; }
    public Map<String, Integer> getGlobalVars() { return globalVars; }
    public Map<String, FunctionDefStmt> getFunctions() { return functions; }
    public List<RouteInfo> getRoutes() { return routes; }
    public List<RenderCall> getRenderCalls() { return renderCalls; }
    public List<UrlForRef> getUrlForRefs() { return urlForRefs; }
    public List<Map<String, Object>> getProductRows() { return productRows; }
    public List<String> getProductFields() { return productFields; }

    public String getAppVariableName() { return appVariableName; }
    public void setAppVariableName(String n) { this.appVariableName = n; }
    public String getProductsVariableName() { return productsVariableName; }
    public void setProductsVariableName(String n) { this.productsVariableName = n; }

    public boolean hasEndpoint(String name) {
        return functions.containsKey(name);
    }

    public boolean hasGlobal(String name) {
        return globalVars.containsKey(name);
    }

    /** Endpoint names that render a given template file. */
    public List<String> endpointsRendering(String templateName) {
        List<String> out = new ArrayList<>();
        for (RenderCall rc : renderCalls) {
            if (rc.getTemplateName().equals(templateName)) out.add(rc.getEndpoint());
        }
        return out;
    }
}
