package tests;

import AST.ASTNode;
import AST.Program;
import AST.template.TemplateVisitor;
import AST.visitor.ProgramVisitor;
import SymbolTable.FlaskSymbolTable;
import SymbolTable.SymbolTableVisitor;
import antlr.FlaskLexer;
import antlr.FlaskParser;
import antlr.TemplateLexer;
import antlr.TemplateParser;
import diagnostics.CompilerErrorListener;
import diagnostics.Diagnostic;
import diagnostics.DiagnosticReporter;
import model.AppModel;
import model.AppModelBuilder;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import semantic.CrossLanguageSemanticAnalyzer;
import semantic.PythonSemanticAnalyzer;
import semantic.TemplateSemanticAnalyzer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shared helpers so tests can drive the real compiler phases from in-memory
 * strings without touching the filesystem. Everything reuses the same classes
 * the production {@code CompilerPipeline} uses.
 */
final class TestSupport {

    private TestSupport() {}

    /** Parses Flask source to an AST, capturing lexer/parser diagnostics. */
    static Program parseFlask(String source, DiagnosticReporter reporter, String file) {
        CharStream input = CharStreams.fromString(source);
        FlaskLexer lexer = new FlaskLexer(input);
        lexer.removeErrorListeners();
        lexer.addErrorListener(new CompilerErrorListener(reporter, file));

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FlaskParser parser = new FlaskParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new CompilerErrorListener(reporter, file));

        FlaskParser.ProgContext tree = parser.prog();
        if (reporter.hasErrors()) return null; // malformed tree; errors already recorded
        return new ProgramVisitor().visit(tree);
    }

    /** Parses a single template (HTML+CSS+Jinja) to an AST. */
    static ASTNode parseTemplate(String source, DiagnosticReporter reporter, String file) {
        CharStream input = CharStreams.fromString(source);
        TemplateLexer lexer = new TemplateLexer(input);
        lexer.removeErrorListeners();
        lexer.addErrorListener(new CompilerErrorListener(reporter, file));

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        TemplateParser parser = new TemplateParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new CompilerErrorListener(reporter, file));

        return new TemplateVisitor().visit(parser.template());
    }

    static FlaskSymbolTable buildSymbolTable(Program program) {
        SymbolTableVisitor v = new SymbolTableVisitor();
        v.visitProgram(program);
        return v.getSymbolTable();
    }

    static AppModel buildModel(Program program) {
        return new AppModelBuilder().build(program);
    }

    /** Full Python semantic run (parse + model + analyzer) returning the reporter. */
    static DiagnosticReporter analyzePython(String source) {
        DiagnosticReporter reporter = new DiagnosticReporter();
        Program program = parseFlask(source, reporter, "test.py");
        AppModel model = buildModel(program);
        new PythonSemanticAnalyzer(reporter, "test.py").analyze(program, model);
        return reporter;
    }

    /** Runs the template analyzer over one template string. */
    static DiagnosticReporter analyzeTemplate(String source, java.util.Set<String> knownTemplates) {
        DiagnosticReporter reporter = new DiagnosticReporter();
        ASTNode root = parseTemplate(source, reporter, "test.html");
        new TemplateSemanticAnalyzer(reporter, "test.html", knownTemplates).analyze(root);
        return reporter;
    }

    /** Runs the cross-language analyzer for a Flask source + a set of templates. */
    static DiagnosticReporter analyzeCross(String flaskSource, Map<String, String> templateSources) {
        DiagnosticReporter reporter = new DiagnosticReporter();
        Program program = parseFlask(flaskSource, reporter, "test.py");
        AppModel model = buildModel(program);
        new PythonSemanticAnalyzer(reporter, "test.py").analyze(program, model);

        Map<String, ASTNode> templates = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : templateSources.entrySet()) {
            templates.put(e.getKey(), parseTemplate(e.getValue(), reporter, e.getKey()));
        }
        new CrossLanguageSemanticAnalyzer(reporter, model, templates, "test.py").analyze();
        return reporter;
    }

    static boolean hasCode(DiagnosticReporter reporter, String code) {
        for (Diagnostic d : reporter.all()) {
            if (code.equals(d.getCode())) return true;
        }
        return false;
    }
}
