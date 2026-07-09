package AST;

import model.AppModel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Exports an architectural data-flow diagram (not a merged AST) showing how
 * Python AST, AppModel, Template AST, semantic analysis, and generation connect.
 */
public final class CrossLanguageDataFlowDot {

    private CrossLanguageDataFlowDot() {}

    public static void write(Path file, AppModel model, int templateCount) throws IOException {
        String products = model == null ? "0" : String.valueOf(model.getProductRows().size());
        String routes = model == null ? "0" : String.valueOf(model.getRoutes().size());
        String renders = model == null ? "0" : String.valueOf(model.getRenderCalls().size());

        String dot = """
                digraph CrossLanguageDataFlow {
                  rankdir=LR;
                  node [shape=box, fontname="Arial"];

                  py_src [label="Flask Source\\n(.py)"];
                  tpl_src [label="Template Sources\\n(.html)"];

                  py_parse [label="FlaskLexer\\nFlaskParser"];
                  tpl_parse [label="TemplateLexer\\nTemplateParser"];

                  py_ast [label="Python AST\\nProgram"];
                  tpl_ast [label="Template AST\\nHtmlDocument + Jinja + CSS\\n(%d files)"];

                  st [label="SymbolTableVisitor\\nFlaskSymbolTable"];
                  amb [label="AppModelBuilder"];
                  am [label="AppModel\\nproducts=%s\\nroutes=%s\\nrender_calls=%s"];

                  py_sem [label="PythonSemanticAnalyzer\\nPY001-PY011"];
                  tpl_sem [label="TemplateSemanticAnalyzer\\nTPL001-TPL010"];
                  cross [label="CrossLanguageSemanticAnalyzer\\nX001-X011"];

                  gen [label="FlaskAppGenerator"];
                  out [label="generated-app/\\napp.py + templates + static"];

                  py_src -> py_parse -> py_ast;
                  tpl_src -> tpl_parse -> tpl_ast;

                  py_ast -> st;
                  py_ast -> amb -> am;
                  py_ast -> py_sem;

                  tpl_ast -> tpl_sem;

                  am -> cross;
                  tpl_ast -> cross;

                  am -> gen;
                  gen -> out;

                  tpl_ast -> gen [style=dashed label="template scaffold"];
                }
                """.formatted(templateCount, products, routes, renders);

        Files.createDirectories(file.getParent());
        Files.writeString(file, dot, StandardCharsets.UTF_8);
    }
}
