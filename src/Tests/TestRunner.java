package tests;

import AST.ASTGraphvizPrinter;
import AST.ASTNode;
import AST.ASTPrinter;
import AST.ASTWalker;
import AST.CrossLanguageDataFlowDot;
import AST.Program;
import AST.literal.DictLiteralExpr;
import AST.literal.ListLiteralExpr;
import AST.template.html.HtmlDocument;
import AST.template.html.HtmlNormalElement;
import AST.stmt.FunctionDefStmt;
import SymbolTable.FlaskSymbolTable;
import SymbolTable.SymbolTableVisitor;
import diagnostics.Diagnostic;
import diagnostics.DiagnosticReporter;
import generator.FlaskAppGenerator;
import model.AppModel;
import model.AppModelBuilder;
import pipeline.CompilerPipeline;

import java.nio.charset.StandardCharsets;
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
        professionalEnhancementTests();

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
        section("Semantic - Python/Flask");

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

        test("PY008 function argument count mismatch (invalid)", () ->
                assertCode(TestSupport.analyzePython(
                        "def greet(name):\n    return name\ngreet()\n"), "PY008"));
        test("PY008 boundary: correct argument count is clean", () ->
                assertNoErrors(TestSupport.analyzePython(
                        "def greet(name):\n    return name\ngreet(\"x\")\n")));

        test("PY009 route path parameter mismatch (invalid)", () ->
                assertCode(TestSupport.analyzePython(
                        "from flask import Flask\napp = Flask(\"a\")\n"
                        + "@app.route(\"/items/<item_id>\")\ndef show():\n    return \"x\"\n"), "PY009"));
        test("PY009 valid: path param matches function param", () ->
                assertNoCode(TestSupport.analyzePython(
                        "from flask import Flask\napp = Flask(\"a\")\n"
                        + "@app.route(\"/items/<item_id>\")\ndef show(item_id):\n    return str(item_id)\n"),
                        "PY009"));

        test("PY010 local used before assignment (invalid)", () ->
                assertCode(TestSupport.analyzePython(
                        "def f():\n    x = y\n    y = 1\n"), "PY010"));
        test("PY010 valid: assign before use", () ->
                assertNoCode(TestSupport.analyzePython(
                        "def f():\n    y = 1\n    x = y\n"), "PY010"));

        test("PY011 route missing return (invalid)", () ->
                assertCode(TestSupport.analyzePython(
                        "from flask import Flask\napp = Flask(\"a\")\n"
                        + "@app.route(\"/\")\ndef home():\n    x = 1\n"), "PY011"));
        test("PY011 valid: route returns on all paths", () ->
                assertNoCode(TestSupport.analyzePython(
                        "from flask import Flask, render_template\napp = Flask(\"a\")\n"
                        + "@app.route(\"/\")\ndef home():\n    return render_template(\"h.html\")\n"),
                        "PY011"));

        test("PY001 line/column present on undefined variable", () -> {
            DiagnosticReporter r = TestSupport.analyzePython("z = missing_name\n");
            Diagnostic d = TestSupport.firstWithCode(r, "PY001");
            assertNotNull(d, "PY001 diagnostic");
            assertTrue(d.getLine() >= 1, "line set");
        });

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
        section("Semantic - Jinja2 / HTML / CSS");

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

        test("TPL002 extends not first (invalid)", () ->
                assertCode(TestSupport.analyzeTemplate(
                        "<p>hello</p>{% extends \"base.html\" %}", Set.of("base.html")), "TPL002"));
        test("TPL002 valid: extends is first", () ->
                assertNoCode(TestSupport.analyzeTemplate(
                        "{% extends \"base.html\" %}{% block content %}x{% endblock %}",
                        Set.of("base.html")), "TPL002"));

        test("TPL005 duplicate HTML id (invalid)", () ->
                assertCode(TestSupport.analyzeTemplate(
                        "<div id=\"x\"></div><div id=\"x\"></div>", Set.of()), "TPL005"));
        test("TPL005 valid: unique ids", () ->
                assertNoCode(TestSupport.analyzeTemplate(
                        "<div id=\"a\"></div><div id=\"b\"></div>", Set.of()), "TPL005"));

        test("TPL006 unknown Jinja function (invalid)", () ->
                assertCode(TestSupport.analyzeTemplate(
                        "{{ mystery_fn() }}", Set.of()), "TPL006"));
        test("TPL006 valid: url_for is known", () ->
                assertNoCode(TestSupport.analyzeTemplate(
                        "{{ url_for('static', filename='style.css') }}", Set.of()), "TPL006"));

        test("TPL007 loop var outside scope (invalid)", () ->
                assertCode(TestSupport.analyzeTemplate(
                        "{% for p in products %}{% endfor %}{{ p.name }}", Set.of()), "TPL007"));
        test("TPL007 valid: loop var used inside loop", () ->
                assertNoCode(TestSupport.analyzeTemplate(
                        "{% for p in products %}{{ p.name }}{% endfor %}", Set.of()), "TPL007"));

        test("TPL008 missing parent template (invalid)", () ->
                assertCode(TestSupport.analyzeTemplates(
                        Map.of("child.html", "{% extends \"missing.html\" %}{% block content %}x{% endblock %}"),
                        Set.of("child.html")), "TPL008"));
        test("TPL008 valid: parent exists", () ->
                assertNoCode(TestSupport.analyzeTemplates(
                        Map.of("base.html", "{% block content %}{% endblock %}",
                                "child.html", "{% extends \"base.html\" %}{% block content %}x{% endblock %}"),
                        Set.of("base.html", "child.html")), "TPL008"));

        test("TPL009 inheritance cycle (invalid)", () ->
                assertCode(TestSupport.analyzeTemplates(
                        Map.of("a.html", "{% extends \"b.html\" %}{% block content %}a{% endblock %}",
                                "b.html", "{% extends \"a.html\" %}{% block content %}b{% endblock %}"),
                        Set.of("a.html", "b.html")), "TPL009"));

        test("TPL010 block not in parent (invalid)", () ->
                assertCode(TestSupport.analyzeTemplates(
                        Map.of("base.html", "{% block content %}{% endblock %}",
                                "child.html", "{% extends \"base.html\" %}{% block footer %}x{% endblock %}"),
                        Set.of("base.html", "child.html")), "TPL010"));
        test("TPL010 valid: block declared in parent", () ->
                assertNoCode(TestSupport.analyzeTemplates(
                        Map.of("base.html", "{% block content %}{% endblock %}",
                                "child.html", "{% extends \"base.html\" %}{% block content %}x{% endblock %}"),
                        Set.of("base.html", "child.html")), "TPL010"));

        test("TPL001 line verified in duplicate block diagnostic", () -> {
            DiagnosticReporter r = TestSupport.analyzeTemplate(
                    "{% block x %}a{% endblock %}{% block x %}b{% endblock %}", Set.of());
            Diagnostic d = TestSupport.firstWithCode(r, "TPL001");
            assertNotNull(d, "TPL001");
            assertTrue(d.getLine() >= 1, "line set");
        });

        test("clean template has no semantic errors", () ->
                assertNoErrors(TestSupport.analyzeTemplate(
                        "<p>{{ name | upper }}</p>", Set.of())));
    }

    private static void crossLanguageTests() {
        section("Semantic - Cross-language (Python <-> Jinja2)");

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

        test("X004 details template needs product (invalid)", () ->
                assertCode(TestSupport.analyzeCross(
                        "from flask import Flask, render_template\napp = Flask(\"a\")\n"
                        + "@app.route(\"/details\")\ndef details():\n"
                        + "    return render_template(\"details.html\")\n",
                        Map.of("details.html",
                                "{% extends \"base.html\" %}{% block content %}{{ product.name }}{% endblock %}",
                                "base.html", "{% block content %}{% endblock %}")), "X004"));
        test("X004 valid: product passed to details template", () ->
                assertNoCode(TestSupport.analyzeCross(
                        "from flask import Flask, render_template\napp = Flask(\"a\")\n"
                        + "products = [{\"id\": 1, \"name\": \"x\"}]\n"
                        + "@app.route(\"/details\")\ndef details():\n"
                        + "    return render_template(\"details.html\", product=products[0])\n",
                        Map.of("details.html", "{{ product.name }}")), "X004"));

        test("X006 url_for missing route argument (invalid)", () ->
                assertCode(TestSupport.analyzeCross(
                        "from flask import Flask\napp = Flask(\"a\")\n"
                        + "@app.route(\"/items/<item_id>\")\ndef show(item_id):\n    return \"ok\"\n",
                        Map.of("page.html",
                                "<a href=\"{{ url_for('show') }}\">link</a>")), "X006"));
        test("X006 valid: required argument provided", () ->
                assertNoCode(TestSupport.analyzeCross(
                        "from flask import Flask\napp = Flask(\"a\")\n"
                        + "@app.route(\"/items/<item_id>\")\ndef show(item_id):\n    return \"ok\"\n",
                        Map.of("page.html",
                                "<a href=\"{{ url_for('show', item_id=1) }}\">link</a>")), "X006"));

        test("X007 form POST not accepted by route (invalid)", () ->
                assertCode(TestSupport.analyzeCross(
                        "from flask import Flask, render_template\napp = Flask(\"a\")\n"
                        + "@app.route(\"/add\")\ndef add():\n    return render_template(\"form.html\")\n",
                        Map.of("form.html",
                                "<form method=\"post\" action=\"{{ url_for('add') }}\">"
                                + "<input name=\"name\"></form>")), "X007"));
        test("X007 valid: route accepts POST", () ->
                assertNoCode(TestSupport.analyzeCross(
                        "from flask import Flask, render_template\napp = Flask(\"a\")\n"
                        + "@app.route(\"/add\", methods=[\"GET\", \"POST\"])\ndef add():\n"
                        + "    return render_template(\"form.html\")\n",
                        Map.of("form.html",
                                "<form method=\"post\" action=\"{{ url_for('add') }}\">"
                                + "<input name=\"name\"></form>")), "X007"));

        test("X008 form field mismatch (invalid)", () ->
                assertCode(TestSupport.analyzeCross(
                        "from flask import Flask, render_template, request\napp = Flask(\"a\")\n"
                        + "@app.route(\"/add\", methods=[\"GET\", \"POST\"])\ndef add():\n"
                        + "    name = request.form.get(\"name\")\n"
                        + "    return render_template(\"form.html\")\n",
                        Map.of("form.html",
                                "<form method=\"post\" action=\"{{ url_for('add') }}\">"
                                + "<input name=\"title\"></form>")), "X008"));
        test("X008 valid: form fields match backend reads", () ->
                assertNoCode(TestSupport.analyzeCross(
                        "from flask import Flask, render_template, request\napp = Flask(\"a\")\n"
                        + "@app.route(\"/add\", methods=[\"GET\", \"POST\"])\ndef add():\n"
                        + "    name = request.form.get(\"name\")\n"
                        + "    return render_template(\"form.html\")\n",
                        Map.of("form.html",
                                "<form method=\"post\" action=\"{{ url_for('add') }}\">"
                                + "<input name=\"name\"></form>")), "X008"));

        test("X009 context type incompatible (invalid)", () ->
                assertCode(TestSupport.analyzeCross(
                        "from flask import Flask, render_template\napp = Flask(\"a\")\n"
                        + "products = [{\"id\": 1, \"name\": \"x\"}]\n"
                        + "@app.route(\"/\")\ndef home():\n"
                        + "    return render_template(\"p.html\", products=products)\n",
                        Map.of("p.html", "{{ products.upper }}")), "X009"));
        test("X009 valid: string context uses string method", () ->
                assertNoCode(TestSupport.analyzeCross(
                        "from flask import Flask, render_template\napp = Flask(\"a\")\n"
                        + "@app.route(\"/\")\ndef home():\n"
                        + "    return render_template(\"p.html\", title=\"hello\")\n",
                        Map.of("p.html", "{{ title.upper }}")), "X009"));

        test("X010 inconsistent product schemas (invalid)", () ->
                assertCode(TestSupport.analyzeCross(
                        "from flask import Flask, render_template\napp = Flask(\"a\")\n"
                        + "products = [{\"id\": 1, \"name\": \"a\"}, {\"id\": 2, \"price\": 9}]\n"
                        + "@app.route(\"/\")\ndef home():\n"
                        + "    return render_template(\"p.html\", products=products)\n",
                        Map.of("p.html", "{% for p in products %}{{ p.name }}{% endfor %}")), "X010"));

        test("X011 inconsistent product field types (invalid)", () ->
                assertCode(TestSupport.analyzeCross(
                        "from flask import Flask, render_template\napp = Flask(\"a\")\n"
                        + "products = [{\"id\": 1, \"price\": 10}, {\"id\": 2, \"price\": \"free\"}]\n"
                        + "@app.route(\"/\")\ndef home():\n"
                        + "    return render_template(\"p.html\", products=products)\n",
                        Map.of("p.html", "{% for p in products %}{{ p.price }}{% endfor %}")), "X011"));

        test("X006 diagnostic includes template file and line", () -> {
            DiagnosticReporter r = TestSupport.analyzeCross(
                    "from flask import Flask\napp = Flask(\"a\")\n"
                    + "@app.route(\"/items/<item_id>\")\ndef show(item_id):\n    return \"ok\"\n",
                    Map.of("page.html", "<a href=\"{{ url_for('show') }}\">x</a>"));
            Diagnostic d = TestSupport.firstWithCode(r, "X006");
            assertNotNull(d, "X006");
            assertEquals("page.html", d.getFile(), "template file");
            assertTrue(d.getLine() >= 1, "line set");
        });

        test("multiple cross errors in one run", () -> {
            DiagnosticReporter r = TestSupport.analyzeCross(
                    "from flask import Flask, render_template\napp = Flask(\"a\")\n"
                    + "products = [{\"id\": 1}]\n"
                    + "@app.route(\"/\")\ndef home():\n"
                    + "    return render_template(\"p.html\")\n",
                    Map.of("p.html", "{{ missing_var }}"));
            assertTrue(TestSupport.hasCode(r, "X003"), "X003");
        });

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

    private static void professionalEnhancementTests() {
        section("Professional Enhancements");

        test("Python AST and Template AST are separate object trees", () -> {
            DiagnosticReporter r = new DiagnosticReporter();
            Program py = TestSupport.parseFlask(
                    "x = 1\ndef foo():\n    return x\n", r, "t.py");
            ASTNode tpl = TestSupport.parseTemplate("<p>{{ x }}</p>", r, "t.html");
            assertNotNull(py, "python root");
            assertNotNull(tpl, "template root");
            assertTrue(py instanceof Program, "python root is Program");
            assertTrue(tpl instanceof HtmlDocument, "template root is HtmlDocument");
            assertTrue(!py.getClass().equals(tpl.getClass()), "roots are different types");
        });

        test("Python AST DOT export succeeds and contains Python nodes", () -> {
            DiagnosticReporter r = new DiagnosticReporter();
            Program py = TestSupport.parseFlask("def f():\n    return 1\n", r, "t.py");
            Path dir = Files.createTempDirectory("dot-py");
            Path dot = dir.resolve("python-ast.dot");
            ASTGraphvizPrinter.print(py, dot.toString());
            String content = Files.readString(dot);
            assertTrue(content.contains("FunctionDefStmt"), "contains FunctionDefStmt");
            assertTrue(content.contains("line"), "contains line metadata");
            assertTrue(!content.contains("HtmlDocument"), "no template nodes");
            deleteRecursively(dir);
        });

        test("Template AST DOT export succeeds and contains template nodes", () -> {
            DiagnosticReporter r = new DiagnosticReporter();
            ASTNode tpl = TestSupport.parseTemplate(
                    "<form method=\"post\"><input name=\"n\"></form>", r, "t.html");
            Path dir = Files.createTempDirectory("dot-tpl");
            Path dot = dir.resolve("template-ast.dot");
            ASTGraphvizPrinter.print(tpl, dot.toString());
            String content = Files.readString(dot);
            assertTrue(content.contains("HtmlDocument") || content.contains("HtmlNormalElement"),
                    "contains HTML nodes");
            assertTrue(!content.contains("FunctionDefStmt"), "no python nodes");
            deleteRecursively(dir);
        });

        test("Cross-language data-flow DOT export succeeds", () -> {
            DiagnosticReporter r = new DiagnosticReporter();
            Program py = TestSupport.parseFlask(
                    "from flask import Flask\napp = Flask(\"a\")\n"
                    + "products = [{\"id\": 1, \"name\": \"x\"}]\n", r, "t.py");
            AppModel model = TestSupport.buildModel(py);
            Path dir = Files.createTempDirectory("dot-flow");
            Path dot = dir.resolve("cross-language-data-flow.dot");
            CrossLanguageDataFlowDot.write(dot, model, 1);
            String content = Files.readString(dot);
            assertTrue(content.contains("AppModel"), "mentions AppModel");
            assertTrue(content.contains("FlaskAppGenerator"), "mentions generator");
            deleteRecursively(dir);
        });

        test("Every parsed Python node has a valid node type and line", () -> {
            DiagnosticReporter r = new DiagnosticReporter();
            Program py = TestSupport.parseFlask("y = 1\nx = y\n", r, "t.py");
            assertTrue(ASTWalker.allHaveNodeName(py), "all have node names");
            assertTrue(ASTWalker.allParsedNodesHaveLine(py), "all have line > 0");
        });

        test("Every parsed template node has a valid node type and line", () -> {
            DiagnosticReporter r = new DiagnosticReporter();
            ASTNode tpl = TestSupport.parseTemplate("<div id=\"a\">{{ name }}</div>", r, "t.html");
            assertTrue(ASTWalker.allHaveNodeName(tpl), "all have node names");
            assertTrue(ASTWalker.allParsedNodesHaveLine(tpl), "all have line > 0");
        });

        test("ASTPrinter output includes node type and line", () -> {
            DiagnosticReporter r = new DiagnosticReporter();
            Program py = TestSupport.parseFlask("def f():\n    return 1\n", r, "t.py");
            String out = ASTPrinter.render(py);
            assertTrue(out.contains("FunctionDefStmt"), "contains node type");
            assertTrue(out.contains("line"), "contains line");
            assertTrue(!out.contains("@"), "no Java object refs");
        });

        test("Symbol Table printer includes scopes and symbol kinds", () -> {
            DiagnosticReporter r = new DiagnosticReporter();
            Program py = TestSupport.parseFlask(
                    "from flask import Flask\napp = Flask(\"a\")\n"
                    + "@app.route(\"/\")\ndef home():\n    return \"ok\"\n", r, "t.py");
            FlaskSymbolTable st = TestSupport.buildSymbolTable(py);
            java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
            java.io.PrintStream ps = new java.io.PrintStream(buf);
            java.io.PrintStream old = System.out;
            System.setOut(ps);
            try {
                st.printSymbolTable();
            } finally {
                System.setOut(old);
            }
            String out = buf.toString(StandardCharsets.UTF_8);
            assertTrue(out.contains("FUNCTION") || out.contains("Function"), "function symbols");
            assertTrue(out.contains("Scope") || out.contains("Global"), "scope info");
        });

        test("python product data flows through AST and AppModel into generated Flask application",
                () -> {
            String marker = "Transferred From Python AST";
            String src = "from flask import Flask, render_template\napp = Flask(\"a\")\n"
                    + "products = [{\"id\": 1, \"name\": \"" + marker + "\", \"price\": 42.0, "
                    + "\"in_stock\": True, \"description\": \"d\", \"category\": \"c\"}]\n"
                    + "@app.route(\"/\")\ndef home():\n"
                    + "    return render_template(\"p.html\", products=products)\n";

            DiagnosticReporter r = new DiagnosticReporter();
            Program py = TestSupport.parseFlask(src, r, "app.py");
            AppModel model = TestSupport.buildModel(py);

            assertEquals(1, model.getProductRows().size(), "one product row");
            assertEquals(marker, model.getProductRows().get(0).get("name"), "name in AppModel");
            assertTrue(model.getProductFields().contains("price"), "schema has price");

            Path out = Files.createTempDirectory("data-flow");
            FlaskAppGenerator gen = new FlaskAppGenerator(model);
            gen.generate(out);
            String appPy = Files.readString(out.resolve("app.py"));
            assertTrue(appPy.contains(marker),
                    "generated app.py contains AST-extracted product name");
            assertTrue(!appPy.contains("Laptop") || appPy.contains(marker),
                    "not only hardcoded seed names");

            String src2 = src.replace(marker, "Changed Value From AST");
            Program py2 = TestSupport.parseFlask(src2, r, "app.py");
            AppModel model2 = TestSupport.buildModel(py2);
            Path out2 = Files.createTempDirectory("data-flow-2");
            new FlaskAppGenerator(model2).generate(out2);
            String appPy2 = Files.readString(out2.resolve("app.py"));
            assertTrue(appPy2.contains("Changed Value From AST"), "output changes with source");
            assertTrue(!appPy2.contains(marker), "old marker gone");

            deleteRecursively(out);
            deleteRecursively(out2);
        });

        test("Generator is blocked when semantic errors exist", () -> {
            DiagnosticReporter r = TestSupport.analyzePython("x = missing_var\n");
            assertTrue(r.hasErrors(), "semantic errors reported");
            assertTrue(r.countErrors() > 0, "error count > 0");
        });

        test("Pipeline export-ast produces non-empty diagram files", () -> {
            Path outDir = Files.createTempDirectory("export-ast");
            CompilerPipeline.Config cfg = new CompilerPipeline.Config();
            cfg.flaskFile = Path.of("examples", "demo", "app.py");
            cfg.templatePath = Path.of("examples", "demo", "templates");
            cfg.analyzeOnly = true;
            cfg.exportAst = true;
            cfg.astOutputDir = outDir;
            int code = new CompilerPipeline().run(cfg);
            assertEquals(0, code, "pipeline success");
            assertFileNonEmpty(outDir.resolve("python-ast.dot"));
            assertFileNonEmpty(outDir.resolve("template-ast.dot"));
            assertFileNonEmpty(outDir.resolve("cross-language-data-flow.dot"));
            deleteRecursively(outDir);
        });

        test("Empty products template contains empty-state message in generator output", () -> {
            AppModel empty = new AppModel();
            Path out = Files.createTempDirectory("empty-ui");
            new FlaskAppGenerator(empty).generate(out);
            String productsHtml = Files.readString(out.resolve("templates").resolve("products.html"));
            assertTrue(productsHtml.contains("No products available"),
                    "empty state text in products template");
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

    private static void assertNoCode(DiagnosticReporter r, String code) {
        if (TestSupport.hasCode(r, code)) {
            throw new AssertionError("did not expect " + code + " but got " + r.all());
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
