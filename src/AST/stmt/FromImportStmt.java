package AST.stmt;

import java.util.List;

public class FromImportStmt extends Statement {

    private String module;
    private List<String> names;

    public FromImportStmt(String module, List<String> names, int line) {
        super("FromImportStmt", line);
        this.module = module;
        this.names = names;
    }

    public String getModule() { return module; }
    public List<String> getNames() { return names; }

    @Override
    public String toString() {
        return "FromImportStmt (line " + lineNumber + ")";
    }

}

