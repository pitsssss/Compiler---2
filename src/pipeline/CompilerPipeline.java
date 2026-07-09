package pipeline;

import AST.ASTNode;
import AST.ASTGraphvizPrinter;
import AST.ASTPrinter;
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
import diagnostics.DiagnosticReporter;
import generator.FlaskAppGenerator;
import model.AppModel;
import model.AppModelBuilder;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import semantic.CrossLanguageSemanticAnalyzer;
import semantic.PythonSemanticAnalyzer;
import semantic.TemplateSemanticAnalyzer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The single, ordered compiler pipeline:
 *
 *   Input files -> Lexer -> Parser -> Parse Tree -> AST -> Symbol Table
 *               -> Semantic Analysis (Python + Template + Cross) -> Generator
 *
 * Keeps no business logic in Main: the CLI simply configures and runs this.
 */
public final class CompilerPipeline {

    public static final class Config {
        public Path flaskFile;
        public Path templatePath;   // file or directory (may be null)
        public Path outputDir = Path.of("generated-app");
        public boolean printPythonAst;
        public boolean printTemplateAst;
        public boolean printSymbolTable;
        public boolean generate;
        public boolean analyzeOnly;
        public Path astDot = Path.of("ast.dot");
    }

    private final DiagnosticReporter reporter = new DiagnosticReporter();

    private Program pythonProgram;
    private AppModel model;
    private FlaskSymbolTable symbolTable;
    private final Map<String, ASTNode> templateAsts = new LinkedHashMap<>();

    public DiagnosticReporter getReporter() { return reporter; }
    public AppModel getModel() { return model; }
    public Program getPythonProgram() { return pythonProgram; }
    public FlaskSymbolTable getSymbolTable() { return symbolTable; }
    public Map<String, ASTNode> getTemplateAsts() { return templateAsts; }

    /** Runs the configured pipeline and returns a process exit code (0 = success). */
    public int run(Config cfg) {
        try {
            if (cfg.flaskFile != null) {
                analyzeFlask(cfg);
            }
            if (cfg.templatePath != null) {
                analyzeTemplates(cfg);
            }
            if (cfg.flaskFile != null && model != null && !templateAsts.isEmpty()) {
                new CrossLanguageSemanticAnalyzer(
                        reporter, model, templateAsts, nameOf(cfg.flaskFile)).analyze();
            }

            reporter.printReport();

            if (reporter.hasErrors()) {
                System.out.println("\nAnalysis FAILED: fix the errors above before generating.");
                return 2;
            }
            System.out.println("\nAnalysis completed successfully.");

            if (cfg.generate && !cfg.analyzeOnly && model != null) {
                generate(cfg);
            }
            return 0;
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            return 3;
        }
    }

    // ---- Flask side --------------------------------------------------------

    private void analyzeFlask(Config cfg) throws IOException {
        String file = nameOf(cfg.flaskFile);
        System.out.println("Parsing Flask source: " + cfg.flaskFile);

        CharStream input = CharStreams.fromPath(cfg.flaskFile);
        FlaskLexer lexer = new FlaskLexer(input);
        lexer.removeErrorListeners();
        lexer.addErrorListener(new CompilerErrorListener(reporter, file));

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        FlaskParser parser = new FlaskParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new CompilerErrorListener(reporter, file));

        FlaskParser.ProgContext tree = parser.prog();

        // If the source has lexical/syntax errors, the parse tree may be
        // malformed. Stop here rather than risk an unhandled exception while
        // building the AST — the reported errors already fail the run cleanly.
        if (reporter.hasErrors()) {
            return;
        }

        pythonProgram = new ProgramVisitor().visit(tree);

        // Symbol table
        SymbolTableVisitor stVisitor = new SymbolTableVisitor();
        stVisitor.visitProgram(pythonProgram);
        symbolTable = stVisitor.getSymbolTable();

        // Application model (routes, render calls, product data, ...)
        model = new AppModelBuilder().build(pythonProgram);

        // Semantic analysis (Python)
        new PythonSemanticAnalyzer(reporter, file).analyze(pythonProgram, model);

        // AST diagram from the real AST
        try {
            ASTGraphvizPrinter.print(pythonProgram, cfg.astDot.toString());
        } catch (IOException e) {
            System.err.println("Could not write ast.dot: " + e.getMessage());
        }

        if (cfg.printPythonAst) {
            ASTPrinter.print("PYTHON / FLASK AST", pythonProgram);
        }
        if (cfg.printSymbolTable) {
            symbolTable.printSymbolTable();
        }
        System.out.println("Routes discovered: " + model.getRoutes());
        System.out.println("Products discovered: " + model.getProductRows().size()
                + " with fields " + model.getProductFields());
    }

    // ---- Template side -----------------------------------------------------

    private void analyzeTemplates(Config cfg) throws IOException {
        List<Path> files = templateFiles(cfg.templatePath);
        if (files.isEmpty()) {
            System.out.println("No template files found at: " + cfg.templatePath);
            return;
        }

        // First pass: parse every template so include/render checks know the set.
        for (Path f : files) {
            ASTNode root = parseTemplate(f);
            if (root != null) templateAsts.put(nameOf(f), root);
        }

        // Second pass: per-template semantic analysis.
        java.util.Set<String> knownNames = templateAsts.keySet();
        for (Map.Entry<String, ASTNode> e : templateAsts.entrySet()) {
            new TemplateSemanticAnalyzer(reporter, e.getKey(), knownNames).analyze(e.getValue());
            if (cfg.printTemplateAst) {
                ASTPrinter.print("TEMPLATE AST: " + e.getKey(), e.getValue());
            }
        }
        // Third pass: inheritance graph (extends / blocks).
        new TemplateSemanticAnalyzer(reporter, "templates", knownNames)
                .analyzeInheritance(templateAsts);
    }

    private ASTNode parseTemplate(Path f) throws IOException {
        System.out.println("Parsing template: " + f);
        CharStream input = CharStreams.fromPath(f);
        TemplateLexer lexer = new TemplateLexer(input);
        lexer.removeErrorListeners();
        lexer.addErrorListener(new CompilerErrorListener(reporter, nameOf(f)));

        CommonTokenStream tokens = new CommonTokenStream(lexer);
        TemplateParser parser = new TemplateParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(new CompilerErrorListener(reporter, nameOf(f)));

        return new TemplateVisitor().visit(parser.template());
    }

    private List<Path> templateFiles(Path path) throws IOException {
        if (Files.isRegularFile(path)) {
            return List.of(path);
        }
        if (Files.isDirectory(path)) {
            try (Stream<Path> s = Files.list(path)) {
                return s.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".html"))
                        .sorted()
                        .toList();
            }
        }
        return List.of();
    }

    // ---- Generation --------------------------------------------------------

    private void generate(Config cfg) throws IOException {
        System.out.println("\nGenerating Flask app into: " + cfg.outputDir);
        List<Path> written = new FlaskAppGenerator(model).generate(cfg.outputDir);
        System.out.println("Generated " + written.size() + " files:");
        for (Path p : written) {
            System.out.println("  - " + cfg.outputDir.relativize(p));
        }
    }

    private static String nameOf(Path p) {
        return p.getFileName().toString();
    }
}
