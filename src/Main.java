import pipeline.CompilerPipeline;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Command-line entry point. Contains no compiler logic itself — it only parses
 * arguments and delegates to {@link CompilerPipeline}.
 *
 * Usage:
 *   Main [options]
 *     --flask &lt;path&gt;         Python/Flask source file to compile
 *     --template &lt;path&gt;      Template file or directory (HTML/CSS/Jinja2)
 *     --output &lt;path&gt;        Output directory for the generated app (default: generated-app)
 *     --print-python-ast     Print the Python/Flask AST
 *     --print-template-ast   Print each template AST
 *     --print-symbol-table   Print the Flask symbol table
 *     --generate             Generate the Flask application
 *     --analyze-only         Analyze only; do not generate
 *     --help                 Show this help
 *
 * With no arguments the built-in demo under examples/demo is used and an app is
 * generated (equivalent to a full end-to-end run).
 */
public class Main {

    public static void main(String[] args) {
        CompilerPipeline.Config cfg = new CompilerPipeline.Config();

        if (args.length == 0) {
            // Sensible demo defaults (relative paths only).
            cfg.flaskFile = Path.of("examples", "demo", "app.py");
            cfg.templatePath = Path.of("examples", "demo", "templates");
            cfg.generate = true;
            cfg.printSymbolTable = true;
            cfg.printPythonAst = true;
            cfg.printTemplateAst = true;
            System.out.println("No arguments given — running built-in demo (examples/demo).");
        } else {
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                switch (a) {
                    case "--help", "-h" -> { printHelp(); return; }
                    case "--flask" -> cfg.flaskFile = Path.of(requireValue(args, ++i, a));
                    case "--template" -> cfg.templatePath = Path.of(requireValue(args, ++i, a));
                    case "--output" -> cfg.outputDir = Path.of(requireValue(args, ++i, a));
                    case "--print-python-ast" -> cfg.printPythonAst = true;
                    case "--print-template-ast" -> cfg.printTemplateAst = true;
                    case "--print-symbol-table" -> cfg.printSymbolTable = true;
                    case "--generate" -> cfg.generate = true;
                    case "--analyze-only" -> cfg.analyzeOnly = true;
                    default -> {
                        System.err.println("Unknown option: " + a);
                        printHelp();
                        System.exit(64);
                    }
                }
            }
        }

        if (cfg.flaskFile == null && cfg.templatePath == null) {
            System.err.println("Error: provide at least --flask <path> or --template <path>.");
            printHelp();
            System.exit(64);
        }
        if (cfg.flaskFile != null && !Files.exists(cfg.flaskFile)) {
            System.err.println("Error: Flask source not found: " + cfg.flaskFile);
            System.exit(66);
        }
        if (cfg.templatePath != null && !Files.exists(cfg.templatePath)) {
            System.err.println("Error: template path not found: " + cfg.templatePath);
            System.exit(66);
        }

        int code = new CompilerPipeline().run(cfg);
        if (code != 0) System.exit(code);
    }

    private static String requireValue(String[] args, int idx, String opt) {
        if (idx >= args.length) {
            System.err.println("Error: option " + opt + " requires a value.");
            System.exit(64);
        }
        return args[idx];
    }

    private static void printHelp() {
        System.out.println("""
            Jinja2Compiler - Flask/Jinja2/HTML/CSS compiler

            Usage:
              Main [options]

            Options:
              --flask <path>         Python/Flask source file to compile
              --template <path>      Template file or directory (HTML/CSS/Jinja2)
              --output <path>        Output dir for generated app (default: generated-app)
              --print-python-ast     Print the Python/Flask AST
              --print-template-ast   Print each template AST
              --print-symbol-table   Print the Flask symbol table
              --generate             Generate the Flask application
              --analyze-only         Analyze only; do not generate
              --help                 Show this help

            Examples:
              Main --flask examples/demo/app.py --template examples/demo/templates --generate
              Main --flask examples/demo/app.py --analyze-only --print-python-ast
              Main            (runs the built-in demo end-to-end)
            """);
    }
}
