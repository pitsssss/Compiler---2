package tests;

import AST.ASTNode;
import AST.Program;
import AST.literal.DictLiteralExpr;
import AST.stmt.FunctionDefStmt;
import SymbolTable.FlaskSymbolTable;
import diagnostics.DiagnosticReporter;
import generator.FlaskAppGenerator;
import model.AppModel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A tiny dependency-free test harness that exercises every compiler phase:
 * lexer, parser, AST, symbol table, semantic analysis (Python + template +
 * cross-language), and the code generator. Prints a Passed/Failed summary and
 * exits non-zero if anything fails, so it can gate CI / build scripts.
 */
public final class TestRunner {

    private static final AtomicInteger passed = new AtomicInteger();
    private static final AtomicInteger failed = new AtomicInteger();

    @FunctionalInterface
    interface Check { void run() throws Exception; }

    public static void main(String[] args) throws Exception {
        System.out.println("==================== COMPILER TEST SUITE ====================");

        lexerParserTests();
        astTests();
        symbolTableTests();
        pythonSemanticTests();
        templateSemanticTests();
        crossLanguageTests();
        generatorTests();

        System.out.println("\n-------------------------------------------------------------");
        System.out.println("Passed: " + passed.get() + "   Failed: " + failed.get());
        System.out.println("=============================================================");
        if (failed.get() > 0) System.exit(1);
    }

    // ======================= sections =======================

    private static void lexerParserTests() {
        section("Lexer / Parser");

        test("valid Flask program parses with no syntax errors", () -> {
            DiagnosticReporter r = new DiagnosticReporter();
            Program p = TestSupport.parseFlask(
                    "x = 1\ndef foo():\n    return x\n", r, "t.py");
            assertNotNull(p, "program");
            assertTrue(!p.getChildren().isEmpty(), "program has children");
            assertEquals(0L, r.countErrors(), "no syntax errors");
        });

        test("invalid Flask program reports a syntax error", () -> {
            DiagnosticReporter r = new DiagnosticReporter();
            TestSupport.parseFlask("def (:\n", r, "t.py");
            assertTrue(r.countErrors() > 0, "expected a syntax error");
        });

        test("multi-line dict/list literal parses (implicit line joining)", () -> {
            DiagnosticReporter r = new DiagnosticReporter();
            TestSupport.parseFlask(
                    "products = [\n    {\n        \"id\": 1,\n        \"name\": \"x\"\n    }\n]\n",
                    r, "t.py");
            assertEquals(0L, r.countErrors(), "no errors on multi-line literal");
        });

        test("valid template parses with no syntax errors", () -> {
            DiagnosticReporter r = new DiagnosticReporter();
            ASTNode root = TestSupport.parseTemplate(
                    "<!DOCTYPE html><html><body><h1>{{ title }}</h1></body></html>", r, "t.html");
            assertNotNull(root, "template root");
            assertEquals(0L, r.countErrors(), "no template syntax errors");
        });
    }

    private static void astTests() {
        section("AST");

        test("function def node carries name, params and a line number", () -> {
            DiagnosticReporter r = new DiagnosticReporter();
            Program p = TestSupport.parseFlask("def greet(a, b):\n    return a\n", r, "t.py");
            FunctionDefStmt fn = firstOfType(p, FunctionDefStmt.class);
            assertNotNull(fn, "function node");
            assertEquals("greet", fn.getName(), "function name");
            assertEquals(List.of("a", "b"), fn.getParameters(), "parameters");
            assertTrue(fn.getLineNumber() >= 1, "line number set");
        });

        test("dict literal produces a DictLiteralExpr node", () -> {
            DiagnosticReporter r = new DiagnosticReporter();
            Program p = TestSupport.parseFlask("d = {\"a\": 1, \"b\": 2}\n", r, "t.py");
            DictLiteralExpr dict = findOfType(p, DictLiteralExpr.class);
            assertNotNull(dict, "dict literal node");
            assertEquals(2, dict.size(), "dict has 2 entries");
        });
    }

    private static void symbolTableTests() {
        section("Symbol Table");

        test("repeated attribute access (app.route) is not a duplicate declaration", () -> {
            DiagnosticReporter r = new DiagnosticReporter();
            Program p = TestSupport.parseFlask(
                    "from flask import Flask\napp = Flask(\"a\")\n"
                    + "@app.route(\"/\")\ndef a():\n    return \"a\"\n"
                    + "@app.route(\"/b\")\ndef b():\n    return \"b\"\n", r, "t.py");
            FlaskSymbolTable st = TestSupport.buildSymbolTable(p);
            // route recorded as a USE, not a scope symbol
            assertTrue(!st.getAttributeUsages().isEmpty(), "attribute usages recorded");
            assertTrue(st.getSymbolsByType(FlaskSymbolTable.SymbolType.FUNCTION).size() >= 2,
                    "both route functions declared");
        });

        test("true duplicate declaration in same scope is rejected", () -> {
            FlaskSymbolTable st = new FlaskSymbolTable();
            boolean first = st.addSymbol(new FlaskSymbolTable.Symbol(
                    "x", FlaskSymbolTable.SymbolType.VARIABLE, 1));
            boolean second = st.addSymbol(new FlaskSymbolTable.Symbol(
                    "x", FlaskSymbolTable.SymbolType.VARIABLE, 2));
            assertTrue(first, "first declaration accepted");
            assertTrue(!second, "duplicate declaration rejected");
        });

        test("built-ins resolve via lookup but are not user declarations", () -> {
            FlaskSymbolTable st = new FlaskSymbolTable();
            assertTrue(st.isBuiltin("append"), "append is a builtin");
            assertTrue(st.isBuiltin("len"), "len is a builtin");
            assertEquals(0, st.getTotalSymbolCount(), "no builtin leaked into declarations");
        });

        test("lookup finds an imported module name", () -> {
            DiagnosticReporter r = new DiagnosticReporter();
            Program p = TestSupport.parseFlask(
                    "from flask import Flask, render_template\napp = Flask(\"a\")\n", r, "t.py");
            FlaskSymbolTable st = TestSupport.buildSymbolTable(p);
            assertTrue(st.isDeclared("app"), "global app declared");
        });
    }

    private static void pythonSemanticTests() {
        section("Semantic — Python/Flask");

        test("PY001 undefined variable", () ->
                assertCode(TestSupport.analyzePython("x = y\n"), "PY001"));
        test("PY001 clean: variable defined before use", () ->
                assertNoErrors(TestSupport.analyzePython("y = 1\nx = y\n")));

        test("PY002 duplicate function definition", () ->
                assertCode(TestSupport.analyzePython(
                        "def foo():\n    return 1\ndef foo():\n    return 2\n"), "PY002"));

        test("PY003 duplicate parameter name", () ->
                assertCode(TestSupport.analyzePython("def f(a, a):\n    return a\n"), "PY003"));

        test("PY004 return outside a function", () ->
                assertCode(TestSupport.analyzePython("x = 1\nreturn x\n"), "PY004"));

        test("PY005 break outside a loop", () ->
                assertCode(TestSupport.analyzePython("break\n"), "PY005"));

        test("PY006 call to undefined function", () ->
                assertCode(TestSupport.analyzePython("bar()\n"), "PY006"));

        test("PY007 duplicate route path", () ->
                assertCode(TestSupport.analyzePython(
                        "from flask import Flask\napp = Flask(\"a\")\n"
                        + "@app.route(\"/\")\ndef a():\n    return \"a\"\n"
                        + "@app.route(\"/\")\ndef b():\n    return \"b\"\n"), "PY007"));

        test("clean Flask program has zero semantic errors", () ->
                assertNoErrors(TestSupport.analyzePython(
                        "from flask import Flask, render_template\napp = Flask(\"a\")\n"
                        + "items = [1, 2, 3]\n"
                        + "@app.route(\"/\")\ndef home():\n    return render_template(\"i.html\", items=items)\n")));

        test("multiple distinct errors collected in a single run", () -> {
            DiagnosticReporter r = TestSupport.analyzePython(
                    "return z\nbreak\n");
            assertTrue(r.countErrors() >= 2, "collects >= 2 errors in one pass");
        });
    }

    private static void templateSemanticTests() {
        section("Semantic — Jinja2 / HTML / CSS");

        test("TPL001 duplicate block name", () ->
                assertCode(TestSupport.analyzeTemplate(
                        "{% block content %}a{% endblock %}{% block content %}b{% endblock %}",
                        Set.of()), "TPL001"));

        test("TPL003 include of a missing template", () ->
                assertCode(TestSupport.analyzeTemplate(
                        "{% include \"nope.html\" %}", Set.of("base.html")), "TPL003"));

        test("TPL004 unknown Jinja filter", () ->
                assertCode(TestSupport.analyzeTemplate(
                        "<p>{{ name | bogusfilter }}</p>", Set.of()), "TPL004"));

        test("clean template has no semantic errors", () ->
                assertNoErrors(TestSupport.analyzeTemplate(
                        "<p>{{ name | upper }}</p>", Set.of())));
    }

    private static void crossLanguageTests() {
        section("Semantic — Cross-language (Python <-> Jinja2)");

        String appMissing =
                "from flask import Flask, render_template\napp = Flask(\"a\")\n"
                + "@app.route(\"/\")\ndef home():\n    return render_template(\"missing.html\")\n";
        test("X001 render_template targets a missing template", () ->
                assertCode(TestSupport.analyzeCross(appMissing, Map.of()), "X001"));

        String appUrlFor =
                "from flask import Flask, render_template, url_for\napp = Flask(\"a\")\n"
                + "@app.route(\"/\")\ndef home():\n    return url_for(\"ghost\")\n";
        test("X002 url_for references an undefined endpoint", () ->
                assertCode(TestSupport.analyzeCross(appUrlFor, Map.of("home.html", "<p>x</p>")), "X002"));

        String appCtx =
                "from flask import Flask, render_template\napp = Flask(\"a\")\n"
                + "@app.route(\"/\")\ndef home():\n    return render_template(\"p.html\")\n";
        test("X003 template uses a context variable never passed", () ->
                assertCode(TestSupport.analyzeCross(appCtx, Map.of("p.html", "<p>{{ foo }}</p>")), "X003"));

        String appKey =
                "from flask import Flask, render_template\napp = Flask(\"a\")\n"
                + "products = [\n    {\"id\": 1, \"name\": \"x\"}\n]\n"
                + "@app.route(\"/\")\ndef home():\n    return render_template(\"list.html\", products=products)\n";
        test("X005 template accesses a product key not in the schema", () ->
                assertCode(TestSupport.analyzeCross(appKey,
                        Map.of("list.html", "{% for p in products %}{{ p.price }}{% endfor %}")), "X005"));

        String appOk =
                "from flask import Flask, render_template\napp = Flask(\"a\")\n"
                + "products = [\n    {\"id\": 1, \"name\": \"x\"}\n]\n"
                + "@app.route(\"/\")\ndef home():\n    return render_template(\"list.html\", products=products)\n";
        test("clean cross-language wiring has no linking errors", () -> {
            DiagnosticReporter r = TestSupport.analyzeCross(appOk,
                    Map.of("list.html", "{% for p in products %}{{ p.name }} {{ p.id }}{% endfor %}"));
            assertTrue(!TestSupport.hasCode(r, "X001"), "no X001");
            assertTrue(!TestSupport.hasCode(r, "X003"), "no X003");
            assertTrue(!TestSupport.hasCode(r, "X005"), "no X005");
        });
    }

    private static void generatorTests() {
        section("Code Generator");

        String app =
                "from flask import Flask, render_template, request, redirect, url_for\napp = Flask(\"app\")\n"
                + "products = [\n"
                + "    {\"id\": 1, \"name\": \"Keyboard\", \"price\": 25.0, \"in_stock\": True}\n"
                + "]\n"
                + "@app.route(\"/products\")\ndef list_products():\n"
                + "    return render_template(\"products.html\", products=products)\n";

        test("generator writes a complete, non-empty Flask project", () -> {
            DiagnosticReporter r = new DiagnosticReporter();
            Program p = TestSupport.parseFlask(app, r, "app.py");
            AppModel model = TestSupport.buildModel(p);
            Path out = Files.createTempDirectory("gen-test");
            List<Path> written = new FlaskAppGenerator(model).generate(out);

            assertTrue(written.size() >= 8, "generated at least 8 files");
            assertFileNonEmpty(out.resolve("app.py"));
            assertFileNonEmpty(out.resolve("requirements.txt"));
            assertFileNonEmpty(out.resolve("templates").resolve("products.html"));
            assertFileNonEmpty(out.resolve("templates").resolve("base.html"));
            assertFileNonEmpty(out.resolve("static").resolve("style.css"));

            String appPy = Files.readString(out.resolve("app.py"));
            assertTrue(appPy.contains("products"), "app.py carries product data");
            assertTrue(appPy.contains("Keyboard"), "product data passed from Python AST");
            assertTrue(appPy.contains("methods=[\"POST\"]") || appPy.contains("methods=['POST']"),
                    "delete uses POST");

            deleteRecursively(out);
        });
    }

    // ======================= helpers =======================

    private static void section(String name) {
        System.out.println("\n-- " + name + " --");
    }

    private static void test(String name, Check check) {
        try {
            check.run();
            passed.incrementAndGet();
            System.out.println("  [PASS] " + name);
        } catch (Throwable t) {
            failed.incrementAndGet();
            System.out.println("  [FAIL] " + name + "  ->  " + t.getMessage());
        }
    }

    private static void assertTrue(boolean cond, String what) {
        if (!cond) throw new AssertionError("expected true: " + what);
    }

    private static void assertNotNull(Object o, String what) {
        if (o == null) throw new AssertionError("expected non-null: " + what);
    }

    private static void assertEquals(Object expected, Object actual, String what) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(what + ": expected <" + expected + "> but was <" + actual + ">");
        }
    }

    private static void assertCode(DiagnosticReporter r, String code) {
        if (!TestSupport.hasCode(r, code)) {
            throw new AssertionError("expected diagnostic " + code + " but got " + r.all());
        }
    }

    private static void assertNoErrors(DiagnosticReporter r) {
        if (r.countErrors() > 0) {
            throw new AssertionError("expected no errors but got " + r.all());
        }
    }

    private static void assertFileNonEmpty(Path p) throws Exception {
        if (!Files.exists(p)) throw new AssertionError("missing file: " + p);
        if (Files.size(p) == 0) throw new AssertionError("empty file: " + p);
    }

    private static <T> T firstOfType(ASTNode root, Class<T> type) {
        for (ASTNode c : root.getChildren()) {
            if (type.isInstance(c)) return type.cast(c);
        }
        return findOfType(root, type);
    }

    private static <T> T findOfType(ASTNode node, Class<T> type) {
        if (type.isInstance(node)) return type.cast(node);
        for (ASTNode c : node.getChildren()) {
            T found = findOfType(c, type);
            if (found != null) return found;
        }
        return null;
    }

    private static void deleteRecursively(Path dir) throws Exception {
        if (!Files.exists(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                .forEach(p -> { try { Files.deleteIfExists(p); } catch (Exception ignored) {} });
        }
    }
}
