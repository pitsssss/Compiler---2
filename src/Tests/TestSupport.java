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
import java.util.Set;

/**
 * Shared helpers so tests can drive the real compiler phases from in-memory
 * strings without touching the filesystem.
 */
final class TestSupport {

    private TestSupport() {}

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
        if (reporter.hasErrors()) return null;
        return new ProgramVisitor().visit(tree);
    }

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

    static DiagnosticReporter analyzePython(String source) {
        DiagnosticReporter reporter = new DiagnosticReporter();
        Program program = parseFlask(source, reporter, "test.py");
        AppModel model = buildModel(program);
        new PythonSemanticAnalyzer(reporter, "test.py").analyze(program, model);
        return reporter;
    }

    static DiagnosticReporter analyzeTemplate(String source, Set<String> knownTemplates) {
        return analyzeTemplates(Map.of("test.html", source), knownTemplates);
    }

    static DiagnosticReporter analyzeTemplates(Map<String, String> sources, Set<String> knownTemplates) {
        DiagnosticReporter reporter = new DiagnosticReporter();
        Map<String, ASTNode> roots = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : sources.entrySet()) {
            roots.put(e.getKey(), parseTemplate(e.getValue(), reporter, e.getKey()));
        }
        Set<String> known = knownTemplates == null ? roots.keySet() : knownTemplates;
        for (Map.Entry<String, ASTNode> e : roots.entrySet()) {
            new TemplateSemanticAnalyzer(reporter, e.getKey(), known).analyze(e.getValue());
        }
        new TemplateSemanticAnalyzer(reporter, "templates", known).analyzeInheritance(roots);
        return reporter;
    }

    static DiagnosticReporter analyzeCross(String flaskSource, Map<String, String> templateSources) {
        DiagnosticReporter reporter = new DiagnosticReporter();
        Program program = parseFlask(flaskSource, reporter, "test.py");
        AppModel model = buildModel(program);
        new PythonSemanticAnalyzer(reporter, "test.py").analyze(program, model);

        Map<String, ASTNode> templates = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : templateSources.entrySet()) {
            templates.put(e.getKey(), parseTemplate(e.getValue(), reporter, e.getKey()));
        }
        Set<String> known = templates.keySet();
        for (Map.Entry<String, ASTNode> e : templates.entrySet()) {
            new TemplateSemanticAnalyzer(reporter, e.getKey(), known).analyze(e.getValue());
        }
        new TemplateSemanticAnalyzer(reporter, "templates", known).analyzeInheritance(templates);
        new CrossLanguageSemanticAnalyzer(reporter, model, templates, "test.py").analyze();
        return reporter;
    }

    static boolean hasCode(DiagnosticReporter reporter, String code) {
        for (Diagnostic d : reporter.all()) {
            if (code.equals(d.getCode())) return true;
        }
        return false;
    }

    static Diagnostic firstWithCode(DiagnosticReporter reporter, String code) {
        for (Diagnostic d : reporter.all()) {
            if (code.equals(d.getCode())) return d;
        }
        return null;
    }

    static boolean hasCodeInFile(DiagnosticReporter reporter, String code, String file) {
        for (Diagnostic d : reporter.all()) {
            if (code.equals(d.getCode()) && file.equals(d.getFile())) return true;
        }
        return false;
    }
}
