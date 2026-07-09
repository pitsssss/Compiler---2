# Environment and Tool Versions

Verified on the development machine used for final professional enhancements (2026-07-09).

## Tested versions

| Tool | Version | How verified |
|------|---------|--------------|
| Java (runtime) | OpenJDK **21.0.11** LTS (Temurin) | `java -version` |
| Java (compiler) | **javac 21.0.11** | `javac -version` |
| Python | **3.14.3** | `python --version` |
| ANTLR | **4.13.2** | Bundled at `lib/antlr-4.13.2-complete.jar` |
| Flask | **>= 3.0** (tested with venv install during `test.ps1`) | `generated-app/requirements.txt` |
| pytest | **>= 8.0** (installed in generated-app venv by `test.ps1`) | `scripts/test.ps1` output |
| PowerShell | **5.1.26100.8655** | `$PSVersionTable.PSVersion` |
| OS | **Windows 10.0.26200** | `[System.Environment]::OSVersion` |

## Minimum requirements

- **JDK 21** with `java` and `javac` on PATH
- **Python 3.10+** (3.14 tested) for generated Flask app tests
- **PowerShell 5.1+** for project scripts
- **ANTLR 4.13.2** JAR in `lib/` (included in repository)

## Optional tools

- **Graphviz `dot`** — renders `.dot` files to SVG:
  ```powershell
  dot -Tsvg docs/diagrams/generated/python-ast.dot -o python-ast.svg
  ```
  If Graphviz is not installed, `full-check.ps1` still passes; DOT files are verified.

## Setup

### JAVA_HOME

Point `JAVA_HOME` to your JDK 21 installation and ensure `%JAVA_HOME%\bin` is on PATH.

### Build classpath

Scripts use: `out;lib/antlr-4.13.2-complete.jar`

### Python virtual environment (generated app tests)

`scripts/test.ps1` creates `generated-app/.venv`, installs `requirements.txt`, and runs pytest.

### Regenerate ANTLR sources

```powershell
.\scripts\generate-antlr.ps1
```

Never edit `src/antlr/*.java` manually.

## Full verification

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
.\scripts\full-check.ps1
```

Expected: `FULL PROJECT CHECK PASSED` with exit code **0**.
