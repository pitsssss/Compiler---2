package SymbolTable;

import java.util.*;

public class FlaskSymbolTable {

    public enum SymbolType {
        VARIABLE,
        FUNCTION,
        PARAMETER,
        ATTRIBUTE,
        FILTER,
        LOOP_VAR,
        BLOCK,
        INCLUDE,
        EXTENDS,
        MODULE,
        CLASS,
        BUILTIN
    }

    public static class Symbol {
        private final String name;
        private final SymbolType type;
        private final String dataType;
        private final int lineNumber;
        private final List<String> parameters;
        private final String context;
        private final String scopeName;

        // Constructor 1: Basic
        public Symbol(String name, SymbolType type, int lineNumber) {
            this(name, type, "unknown", lineNumber, null, null, null);
        }

        // Constructor 2: With data type
        public Symbol(String name, SymbolType type, String dataType, int lineNumber) {
            this(name, type, dataType, lineNumber, null, null, null);
        }

        // Constructor 3: With parameters
        public Symbol(String name, SymbolType type, int lineNumber, List<String> parameters) {
            this(name, type, "unknown", lineNumber, parameters, null, null);
        }

        // Constructor 4: With context and scope
        public Symbol(String name, SymbolType type, int lineNumber, String context, String scopeName) {
            this(name, type, "unknown", lineNumber, null, context, scopeName);
        }

        // Constructor 5: With parameters, context and scope (المطلوب)
        public Symbol(String name, SymbolType type, int lineNumber, List<String> parameters, String context, String scopeName) {
            this(name, type, "unknown", lineNumber, parameters, context, scopeName);
        }

        // Constructor 6: Complete
        public Symbol(String name, SymbolType type, String dataType, int lineNumber,
                      List<String> parameters, String context, String scopeName) {
            this.name = name;
            this.type = type;
            this.dataType = dataType;
            this.lineNumber = lineNumber;
            this.parameters = parameters;
            this.context = context;
            this.scopeName = scopeName;
        }

        public String getName() { return name; }
        public SymbolType getType() { return type; }
        public String getDataType() { return dataType; }
        public int getLineNumber() { return lineNumber; }
        public List<String> getParameters() { return parameters; }
        public String getContext() { return context; }
        public String getScopeName() { return scopeName; }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(name);

            if (!dataType.equals("unknown")) {
                sb.append(" : ").append(dataType);
            }

            if (parameters != null && !parameters.isEmpty()) {
                sb.append("(").append(String.join(", ", parameters)).append(")");
            }

            sb.append(" [").append(type).append("]");
            sb.append(" [line ").append(lineNumber).append("]");

            if (scopeName != null && !scopeName.equals("global")) {
                sb.append(" [scope: ").append(scopeName).append("]");
            }

            return sb.toString();
        }
    }

    // ===== Scope =====
    public static class Scope {
        private final String name;
        private final ScopeType scopeType;
        private final Map<String, Symbol> symbols = new LinkedHashMap<>();
        private final List<Scope> childScopes = new ArrayList<>();

        public enum ScopeType {
            GLOBAL, FUNCTION, LOOP, IF, BLOCK, CLASS, MODULE
        }

        public Scope(String name, ScopeType scopeType) {
            this.name = name;
            this.scopeType = scopeType;
        }

        public boolean contains(String name) {
            return symbols.containsKey(name);
        }

        public void addSymbol(Symbol s) {
            symbols.put(s.getName(), s);
        }

        public Symbol lookup(String name) {
            return symbols.get(name);
        }

        public String getName() { return name; }
        public ScopeType getScopeType() { return scopeType; }
        public Collection<Symbol> getSymbols() { return symbols.values(); }
        public List<Scope> getChildScopes() { return childScopes; }
        public void addChildScope(Scope child) { childScopes.add(child); }

        public int getSymbolCount() {
            return symbols.size();
        }
    }

    private final Deque<Scope> scopeStack = new ArrayDeque<>();
    private final List<Symbol> allSymbols = new ArrayList<>();
    private final Map<String, List<Symbol>> symbolsByType = new HashMap<>();
    private final Map<String, Symbol> builtins = new HashMap<>();

    // Uses (not declarations) tracked separately so they never cause duplicates.
    private final List<Symbol> attributeUsages = new ArrayList<>();
    private final List<Symbol> builtinUsages = new ArrayList<>();

    private int scopeCounter = 0;
    private final Set<String> symbolNames = new HashSet<>();

    public FlaskSymbolTable() {
        enterScope("global", Scope.ScopeType.GLOBAL);
        initializeBuiltins();
    }

    private void initializeBuiltins() {
        // Flask built-ins
        addBuiltin("Flask", SymbolType.MODULE, "module");
        addBuiltin("render_template", SymbolType.FUNCTION, "function");
        addBuiltin("request", SymbolType.MODULE, "module");
        addBuiltin("redirect", SymbolType.FUNCTION, "function");
        addBuiltin("url_for", SymbolType.FUNCTION, "function");

        // Python built-ins
        addBuiltin("len", SymbolType.BUILTIN, "function");
        addBuiltin("int", SymbolType.BUILTIN, "function");
        addBuiltin("str", SymbolType.BUILTIN, "function");
        addBuiltin("list", SymbolType.BUILTIN, "function");
        addBuiltin("dict", SymbolType.BUILTIN, "function");
        addBuiltin("bool", SymbolType.BUILTIN, "function");
        addBuiltin("float", SymbolType.BUILTIN, "function");
        addBuiltin("print", SymbolType.BUILTIN, "function");
        addBuiltin("range", SymbolType.BUILTIN, "function");

        // Common methods
        addBuiltin("append", SymbolType.BUILTIN, "method");
        addBuiltin("get", SymbolType.BUILTIN, "method");
        addBuiltin("keys", SymbolType.BUILTIN, "method");
        addBuiltin("values", SymbolType.BUILTIN, "method");
    }

    private void addBuiltin(String name, SymbolType type, String dataType) {
        // Registered for name-resolution only; NOT added to the scope symbol
        // inventory so that Flask/Python built-ins never masquerade as user
        // declarations or trigger duplicate warnings.
        Symbol symbol = new Symbol(name, type, dataType, 0);
        builtins.put(name, symbol);
    }

    public boolean isBuiltin(String name) {
        return builtins.containsKey(cleanSymbolName(name));
    }

    // ===== Scope Management =====
    public void enterScope(String baseName, Scope.ScopeType scopeType) {
        scopeCounter++;
        String uniqueName = baseName + "_" + scopeCounter;
        Scope newScope = new Scope(uniqueName, scopeType);

        if (!scopeStack.isEmpty()) {
            Scope current = scopeStack.peek();
            current.addChildScope(newScope);
        }

        scopeStack.push(newScope);
    }

    public void exitScope() {
        if (!scopeStack.isEmpty() && scopeStack.size() > 1) {
            scopeStack.pop();
        }
    }

    public Scope getCurrentScope() {
        return scopeStack.peek();
    }

    public String getCurrentScopeName() {
        Scope current = getCurrentScope();
        return current != null ? current.getName() : "global";
    }

    // ===== Symbol Management =====
    private String cleanSymbolName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "";
        }

        name = name.trim();

        // إزالة الأقواس والرموز الخاصة
        name = name.replaceAll("[\\)\\(\\[\\]\\{\\}\"']", "");

        // إذا كان يحتوي على نقطة، نأخذ الجزء الأخير فقط للسمات
        if (name.contains(".")) {
            String[] parts = name.split("\\.");
            name = parts[parts.length - 1];
        }

        return name;
    }

    /** Adds a real declaration to the current scope.
     *  @return true if added, false if a same-scope duplicate blocked it. */
    public boolean addSymbol(Symbol symbol) {
        if (symbol == null || symbol.getName() == null) {
            return false;
        }

        String cleanedName = cleanSymbolName(symbol.getName());
        if (cleanedName.isEmpty()) {
            return false;
        }

        // إنشاء رمز جديد بالاسم النظيف
        Symbol cleanedSymbol = new Symbol(
                cleanedName,
                symbol.getType(),
                symbol.getDataType(),
                symbol.getLineNumber(),
                symbol.getParameters(),
                symbol.getContext(),
                symbol.getScopeName() != null ? symbol.getScopeName() : getCurrentScopeName()
        );

        Scope current = getCurrentScope();
        if (current != null) {
            // A genuine re-declaration in the SAME scope: keep the first symbol
            // and record the collision for the semantic analyzer. We do NOT print
            // here — diagnostics are the single source of truth for user output.
            if (current.contains(cleanedName)) {
                return false;
            }

            current.addSymbol(cleanedSymbol);
            symbolNames.add(cleanedName);
            allSymbols.add(cleanedSymbol);
            addToCategory(cleanedSymbol);
            return true;
        }
        return false;
    }

    private void addToCategory(Symbol symbol) {
        SymbolType type = symbol.getType();
        String typeName = type.toString().toLowerCase();

        symbolsByType.putIfAbsent(typeName, new ArrayList<>());
        symbolsByType.get(typeName).add(symbol);
    }

    // ===== Helper Methods =====
    public void addImport(String name, int lineNumber) {
        addSymbol(new Symbol(name, SymbolType.MODULE, lineNumber, "import", "global"));
    }

    public void addGlobalVariable(String name, int lineNumber) {
        addSymbol(new Symbol(name, SymbolType.VARIABLE, lineNumber, "global", "global"));
    }

    public void addLocalVariable(String name, int lineNumber) {
        addSymbol(new Symbol(name, SymbolType.VARIABLE, lineNumber, "local", getCurrentScopeName()));
    }

    public void addLoopVariable(String name, int lineNumber) {
        addSymbol(new Symbol(name, SymbolType.LOOP_VAR, lineNumber, "loop", getCurrentScopeName()));
    }

    public void addParameter(String name, int lineNumber) {
        addSymbol(new Symbol(name, SymbolType.PARAMETER, lineNumber, "parameter", getCurrentScopeName()));
    }

    // Attribute accesses (e.g. app.route, request.args) are USES, not declarations.
    // They are recorded in a dedicated list and never placed in a scope, so
    // repeated access can never look like a duplicate declaration.
    public void addAttribute(String base, String attribute, int lineNumber) {
        String key = base + "." + attribute;
        for (Symbol s : attributeUsages) {
            if (s.getName().equals(attribute) && s.getDataType().equals(base)
                    && s.getLineNumber() == lineNumber) {
                return; // already recorded this exact usage
            }
        }
        attributeUsages.add(new Symbol(attribute, SymbolType.ATTRIBUTE, base, lineNumber,
                null, "attribute", getCurrentScopeName()));
    }

    // هذه الدالة المحدثة والمصححة
    public void addFunction(String name, List<String> parameters, int lineNumber) {
        addSymbol(new Symbol(
                name,
                SymbolType.FUNCTION,
                lineNumber,
                parameters,
                "function",
                getCurrentScopeName()
        ));
    }

    // Built-in calls are USES, not declarations: recorded once per name.
    public void addBuiltinFunction(String name, int lineNumber) {
        for (Symbol s : builtinUsages) {
            if (s.getName().equals(name)) return;
        }
        builtinUsages.add(new Symbol(name, SymbolType.BUILTIN, lineNumber, "builtin", "global"));
    }

    public List<Symbol> getAttributeUsages() { return attributeUsages; }
    public List<Symbol> getBuiltinUsages() { return builtinUsages; }

    // ===== Lookup Methods =====
    public Symbol lookup(String name) {
        if (name == null) return null;

        String cleanedName = cleanSymbolName(name);

        // البحث من الأعلى إلى الأسفل في الـ scopes
        for (Scope scope : scopeStack) {
            Symbol s = scope.lookup(cleanedName);
            if (s != null) return s;
        }

        // البحث في built-ins
        if (builtins.containsKey(cleanedName)) {
            return builtins.get(cleanedName);
        }

        return null;
    }

    public boolean isDeclared(String name) {
        return lookup(name) != null;
    }

    // ===== Print Methods =====
    public void printSymbolTable() {
        System.out.println("\n" + "=".repeat(20) + " FLASK SYMBOL TABLE " + "=".repeat(20));

        // Imports/Modules
        printCategory("Imports", getSymbolsByType(SymbolType.MODULE));

        // Global Variables
        List<Symbol> globals = getGlobalVariables();
        if (!globals.isEmpty()) {
            System.out.println("\nGlobal Variables:");
            for (Symbol s : globals) {
                System.out.println("  - " + s.getName() + " [line " + s.getLineNumber() + "]");
            }
        }

        // Functions
        List<Symbol> functions = getSymbolsByType(SymbolType.FUNCTION);
        if (!functions.isEmpty()) {
            System.out.println("\nFunctions:");
            for (Symbol f : functions) {
                String params = f.getParameters() != null && !f.getParameters().isEmpty() ?
                        "(" + String.join(", ", f.getParameters()) + ")" : "()";
                System.out.println("  - " + f.getName() + params + " [line " + f.getLineNumber() + "]");
            }
        }

        // Local Variables (grouped by scope)
        printLocalVariables();

        // Built-in Functions actually used (uses, not declarations)
        if (!builtinUsages.isEmpty()) {
            System.out.println("\nBuilt-in Functions (used):");
            for (Symbol b : builtinUsages) {
                System.out.println("  - " + b.getName() + "()");
            }
        }

        // Attribute accesses (uses, not declarations)
        if (!attributeUsages.isEmpty()) {
            System.out.println("\nAttribute Accesses (uses, not declarations):");
            for (Symbol a : attributeUsages) {
                System.out.println("  - " + a.getDataType() + "." + a.getName() +
                        " [line " + a.getLineNumber() + "]");
            }
        }

        // Empty categories
        printEmptyCategory("Blocks", SymbolType.BLOCK);
        printEmptyCategory("Loop Variables", SymbolType.LOOP_VAR);
        printEmptyCategory("Filters", SymbolType.FILTER);
        printEmptyCategory("Includes", SymbolType.INCLUDE);
        printEmptyCategory("Extends", SymbolType.EXTENDS);

        System.out.println("\n" + "=".repeat(60));
        System.out.println("Total symbols: " + allSymbols.size());
    }

    private void printCategory(String title, List<Symbol> symbols) {
        if (symbols != null && !symbols.isEmpty()) {
            System.out.println("\n" + title + ":");
            for (Symbol s : symbols) {
                System.out.println("  - " + s.toString());
            }
        }
    }

    private void printEmptyCategory(String title, SymbolType type) {
        List<Symbol> symbols = getSymbolsByType(type);
        if (symbols == null || symbols.isEmpty()) {
            System.out.println("\n" + title + ":");
            System.out.println("  (none)");
        }
    }

    private void printLocalVariables() {
        Map<String, List<Symbol>> localsByScope = new HashMap<>();

        for (Symbol s : allSymbols) {
            if (s.getType() == SymbolType.VARIABLE &&
                    !"global".equals(s.getScopeName()) &&
                    !"global".equals(s.getContext())) {
                localsByScope.putIfAbsent(s.getScopeName(), new ArrayList<>());
                localsByScope.get(s.getScopeName()).add(s);
            }
        }

        if (!localsByScope.isEmpty()) {
            System.out.println("\nLocal Variables:");
            for (Map.Entry<String, List<Symbol>> entry : localsByScope.entrySet()) {
                System.out.println("  In " + entry.getKey() + ":");
                for (Symbol s : entry.getValue()) {
                    System.out.println("    - " + s.getName() + " [line " + s.getLineNumber() + "]");
                }
            }
        }
    }

    // ===== Getters =====
    public List<Symbol> getGlobalVariables() {
        List<Symbol> result = new ArrayList<>();
        for (Symbol s : allSymbols) {
            if (s.getType() == SymbolType.VARIABLE && "global".equals(s.getContext())) {
                result.add(s);
            }
        }
        return result;
    }

    public List<Symbol> getSymbolsByType(SymbolType type) {
        String typeName = type.toString().toLowerCase();
        return symbolsByType.getOrDefault(typeName, new ArrayList<>());
    }

    public List<Symbol> getAllSymbols() {
        return new ArrayList<>(allSymbols);
    }

    public int getTotalSymbolCount() {
        return allSymbols.size();
    }

    public void clear() {
        scopeStack.clear();
        allSymbols.clear();
        symbolsByType.clear();
        symbolNames.clear();
        attributeUsages.clear();
        builtinUsages.clear();
        scopeCounter = 0;
        enterScope("global", Scope.ScopeType.GLOBAL);
        initializeBuiltins();
    }
}