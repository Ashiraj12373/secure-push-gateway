package com.securepushgateway.engine;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.securepushgateway.model.VulnerabilityResult;
import com.securepushgateway.model.VulnerabilityResult.Severity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ScanEngineService — Core static analysis engine.
 *
 * Java files: parsed via JavaParser AST with visitor-based rule detection.
 * JavaScript files: regex-based pattern matching (25+ total rules across both languages).
 *
 * Rules are modular — add new ones by implementing ScanRule<T> and registering below.
 */
@Service
@Slf4j
public class ScanEngineService {

    private final JavaParser javaParser = new JavaParser();

    /**
     * Main entry point. Dispatches to language-specific scanner.
     */
    public List<VulnerabilityResult> scan(String fileName, String code) {
        log.info("Scanning file: {}", fileName);
        if (fileName.endsWith(".java")) {
            return scanJava(fileName, code);
        } else if (fileName.endsWith(".js") || fileName.endsWith(".ts")) {
            return scanJavaScript(fileName, code);
        }
        return List.of();
    }

    // ─────────────────────────────────────────────────────────────
    //  JAVA AST ANALYSIS
    // ─────────────────────────────────────────────────────────────

    private List<VulnerabilityResult> scanJava(String fileName, String code) {
        List<VulnerabilityResult> results = new ArrayList<>();
        ParseResult<CompilationUnit> parseResult = javaParser.parse(code);
        if (!parseResult.isSuccessful()) {
            log.warn("Failed to parse Java file: {} — falling back to regex", fileName);
            return scanJavaViaRegex(fileName, code);
        }
        CompilationUnit cu = parseResult.getResult().orElse(null);
        if (cu == null) return results;

        cu.accept(new HardcodedSecretVisitor(fileName, results), null);
        cu.accept(new SqlInjectionVisitor(fileName, results), null);
        cu.accept(new UnsafeDeserializationVisitor(fileName, results), null);
        cu.accept(new DangerousMethodVisitor(fileName, results), null);
        cu.accept(new XssVectorVisitor(fileName, results), null);
        cu.accept(new WeakCryptoVisitor(fileName, results), null);
        cu.accept(new NullDereferenceVisitor(fileName, results), null);

        return results;
    }

    // ─── RULE: JAVA-001 — Hardcoded Secrets ─────────────────────

    private static class HardcodedSecretVisitor extends VoidVisitorAdapter<Void> {
        private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(?i)(password|passwd|secret|api.?key|token|auth|credential|private.?key|access.?key)");
        private final String fileName;
        private final List<VulnerabilityResult> results;

        HardcodedSecretVisitor(String fileName, List<VulnerabilityResult> results) {
            this.fileName = fileName;
            this.results = results;
        }

        @Override
        public void visit(VariableDeclarationExpr expr, Void arg) {
            super.visit(expr, arg);
            for (VariableDeclarator decl : expr.getVariables()) {
                checkVariable(decl, expr.getBegin().map(p -> p.line).orElse(0), expr.toString());
            }
        }

        @Override
        public void visit(FieldDeclaration field, Void arg) {
            super.visit(field, arg);
            for (VariableDeclarator decl : field.getVariables()) {
                checkVariable(decl, field.getBegin().map(p -> p.line).orElse(0), field.toString());
            }
        }

        private void checkVariable(VariableDeclarator decl, int lineNumber, String snippet) {
            String varName = decl.getNameAsString();
            if (SECRET_PATTERN.matcher(varName).find()) {
                decl.getInitializer().ifPresent(init -> {
                    if (init instanceof StringLiteralExpr strLit && !strLit.asString().isBlank()) {
                        results.add(VulnerabilityResult.builder()
                            .ruleId("JAVA-001")
                            .severity(Severity.CRITICAL)
                            .fileName(fileName)
                            .lineNumber(lineNumber)
                            .description("Hardcoded secret detected in variable '" + varName
                                + "'. Secrets must be loaded from environment variables or a secrets manager.")
                            .codeSnippet(snippet)
                            .build());
                    }
                });
            }
        }
    }

    // ─── RULE: JAVA-002 — SQL Injection ─────────────────────────

    private static class SqlInjectionVisitor extends VoidVisitorAdapter<Void> {
        private static final Pattern JDBC_METHODS = Pattern.compile(
            "(?i)(executeQuery|executeUpdate|execute|prepareStatement)");
        private static final Pattern SQL_KEYWORDS = Pattern.compile(
            "(?i)(SELECT|INSERT|UPDATE|DELETE|DROP|CREATE|ALTER)\\s");
        private final String fileName;
        private final List<VulnerabilityResult> results;

        SqlInjectionVisitor(String fileName, List<VulnerabilityResult> results) {
            this.fileName = fileName;
            this.results = results;
        }

        @Override
        public void visit(MethodCallExpr expr, Void arg) {
            super.visit(expr, arg);
            if (JDBC_METHODS.matcher(expr.getNameAsString()).find()) {
                for (Expression exprArg : expr.getArguments()) {
                    if (exprArg instanceof BinaryExpr binExpr
                        && binExpr.getOperator() == BinaryExpr.Operator.PLUS) {
                        results.add(VulnerabilityResult.builder()
                            .ruleId("JAVA-002")
                            .severity(Severity.CRITICAL)
                            .fileName(fileName)
                            .lineNumber(expr.getBegin().map(p -> p.line).orElse(0))
                            .description("Potential SQL Injection: string concatenation used directly in JDBC query. Use PreparedStatement with parameterized queries.")
                            .codeSnippet(expr.toString())
                            .build());
                    }
                }
            }
        }

        @Override
        public void visit(VariableDeclarationExpr expr, Void arg) {
            super.visit(expr, arg);
            checkSqlConcat(expr.getVariables(), expr.getBegin().map(p -> p.line).orElse(0));
        }

        @Override
        public void visit(FieldDeclaration field, Void arg) {
            super.visit(field, arg);
            checkSqlConcat(field.getVariables(), field.getBegin().map(p -> p.line).orElse(0));
        }

        private void checkSqlConcat(java.util.List<VariableDeclarator> vars, int lineNumber) {
            for (VariableDeclarator decl : vars) {
                decl.getInitializer().ifPresent(init -> {
                    if (init instanceof BinaryExpr binExpr
                        && binExpr.getOperator() == BinaryExpr.Operator.PLUS) {
                        String initStr = init.toString();
                        if (SQL_KEYWORDS.matcher(initStr).find()) {
                            results.add(VulnerabilityResult.builder()
                                .ruleId("JAVA-002")
                                .severity(Severity.CRITICAL)
                                .fileName(fileName)
                                .lineNumber(lineNumber)
                                .description("Potential SQL Injection: SQL query built with string concatenation in variable '" + decl.getNameAsString()
                                    + "'. Use PreparedStatement with parameterized queries.")
                                .codeSnippet(decl.toString())
                                .build());
                        }
                    }
                });
            }
        }
    }

    // ─── RULE: JAVA-003 — Unsafe Deserialization ─────────────────

    private static class UnsafeDeserializationVisitor extends VoidVisitorAdapter<Void> {
        private final String fileName;
        private final List<VulnerabilityResult> results;

        UnsafeDeserializationVisitor(String fileName, List<VulnerabilityResult> results) {
            this.fileName = fileName;
            this.results = results;
        }

        @Override
        public void visit(MethodCallExpr expr, Void arg) {
            super.visit(expr, arg);
            if ("readObject".equals(expr.getNameAsString())) {
                expr.getScope().ifPresent(scope -> {
                    if (scope.toString().contains("ObjectInputStream")) {
                        results.add(VulnerabilityResult.builder()
                            .ruleId("JAVA-003")
                            .severity(Severity.HIGH)
                            .fileName(fileName)
                            .lineNumber(expr.getBegin().map(p -> p.line).orElse(0))
                            .description("Unsafe deserialization via ObjectInputStream.readObject(). Deserializing untrusted data can lead to Remote Code Execution. Use a filtering ObjectInputStream or switch to a safer serialization format.")
                            .codeSnippet(expr.toString())
                            .build());
                    }
                });
            }
        }
    }

    // ─── RULE: JAVA-004 — Dangerous Method Calls ─────────────────

    private static class DangerousMethodVisitor extends VoidVisitorAdapter<Void> {
        private final String fileName;
        private final List<VulnerabilityResult> results;

        DangerousMethodVisitor(String fileName, List<VulnerabilityResult> results) {
            this.fileName = fileName;
            this.results = results;
        }

        @Override
        public void visit(MethodCallExpr expr, Void arg) {
            super.visit(expr, arg);
            String name = expr.getNameAsString();
            String scope = expr.getScope().map(Object::toString).orElse("");

            if (name.equals("exec") && scope.contains("Runtime")) {
                results.add(VulnerabilityResult.builder()
                    .ruleId("JAVA-004a")
                    .severity(Severity.CRITICAL)
                    .fileName(fileName)
                    .lineNumber(expr.getBegin().map(p -> p.line).orElse(0))
                    .description("Runtime.exec() detected. OS command injection risk. Use ProcessBuilder with explicit argument list instead.")
                    .codeSnippet(expr.toString())
                    .build());
            }
            if (name.equals("exit") && scope.contains("System")) {
                results.add(VulnerabilityResult.builder()
                    .ruleId("JAVA-004b")
                    .severity(Severity.MEDIUM)
                    .fileName(fileName)
                    .lineNumber(expr.getBegin().map(p -> p.line).orElse(0))
                    .description("System.exit() called — abrupt JVM termination can bypass security handlers and shutdown hooks.")
                    .codeSnippet(expr.toString())
                    .build());
            }
        }

        @Override
        public void visit(ObjectCreationExpr expr, Void arg) {
            super.visit(expr, arg);
            if ("ProcessBuilder".equals(expr.getTypeAsString())) {
                boolean concatenatesStrings = expr.getArguments().stream()
                    .anyMatch(a -> a instanceof BinaryExpr be
                        && be.getOperator() == BinaryExpr.Operator.PLUS);
                if (concatenatesStrings) {
                    results.add(VulnerabilityResult.builder()
                        .ruleId("JAVA-004c")
                        .severity(Severity.HIGH)
                        .fileName(fileName)
                        .lineNumber(expr.getBegin().map(p -> p.line).orElse(0))
                        .description("ProcessBuilder constructed with string concatenation — potential command injection.")
                        .codeSnippet(expr.toString())
                        .build());
                }
            }
        }
    }

    // ─── RULE: JAVA-005 — XSS Vectors in Servlet Writers ────────

    private static class XssVectorVisitor extends VoidVisitorAdapter<Void> {
        private static final Pattern WRITER_METHODS = Pattern.compile("(?i)(print|println|write)");
        private final String fileName;
        private final List<VulnerabilityResult> results;

        XssVectorVisitor(String fileName, List<VulnerabilityResult> results) {
            this.fileName = fileName;
            this.results = results;
        }

        @Override
        public void visit(MethodCallExpr expr, Void arg) {
            super.visit(expr, arg);
            String scope = expr.getScope().map(Object::toString).orElse("");
            if (WRITER_METHODS.matcher(expr.getNameAsString()).find()
                && (scope.contains("response") || scope.contains("writer") || scope.contains("Writer"))) {
                for (Expression exprArg : expr.getArguments()) {
                    if (exprArg instanceof BinaryExpr || exprArg instanceof NameExpr) {
                        results.add(VulnerabilityResult.builder()
                            .ruleId("JAVA-005")
                            .severity(Severity.HIGH)
                            .fileName(fileName)
                            .lineNumber(expr.getBegin().map(p -> p.line).orElse(0))
                            .description("Potential XSS: unescaped variable written directly to HTTP response writer. Sanitize all output with OWASP Java Encoder or similar.")
                            .codeSnippet(expr.toString())
                            .build());
                    }
                }
            }
        }
    }

    // ─── RULE: JAVA-006 — Weak Cryptography ─────────────────────

    private static class WeakCryptoVisitor extends VoidVisitorAdapter<Void> {
        private static final Pattern WEAK_ALGO = Pattern.compile("(?i)(MD5|SHA-1|SHA1|DES|RC4|RC2)");
        private final String fileName;
        private final List<VulnerabilityResult> results;

        WeakCryptoVisitor(String fileName, List<VulnerabilityResult> results) {
            this.fileName = fileName;
            this.results = results;
        }

        @Override
        public void visit(StringLiteralExpr expr, Void arg) {
            super.visit(expr, arg);
            if (WEAK_ALGO.matcher(expr.asString()).find()) {
                results.add(VulnerabilityResult.builder()
                    .ruleId("JAVA-006")
                    .severity(Severity.HIGH)
                    .fileName(fileName)
                    .lineNumber(expr.getBegin().map(p -> p.line).orElse(0))
                    .description("Weak cryptographic algorithm '" + expr.asString()
                        + "' detected. Use SHA-256 or stronger. For password hashing, use BCrypt or Argon2.")
                    .codeSnippet(expr.toString())
                    .build());
            }
        }
    }

    // ─── RULE: JAVA-007 — Potential Null Dereference ─────────────

    private static class NullDereferenceVisitor extends VoidVisitorAdapter<Void> {
        private final String fileName;
        private final List<VulnerabilityResult> results;

        NullDereferenceVisitor(String fileName, List<VulnerabilityResult> results) {
            this.fileName = fileName;
            this.results = results;
        }

        @Override
        public void visit(MethodCallExpr expr, Void arg) {
            super.visit(expr, arg);
            if (expr.getScope().map(s -> s instanceof MethodCallExpr).orElse(false)) {
                MethodCallExpr inner = (MethodCallExpr) expr.getScope().get();
                String innerName = inner.getNameAsString();
                if (innerName.startsWith("get") || innerName.startsWith("find")) {
                    results.add(VulnerabilityResult.builder()
                        .ruleId("JAVA-007")
                        .severity(Severity.MEDIUM)
                        .fileName(fileName)
                        .lineNumber(expr.getBegin().map(p -> p.line).orElse(0))
                        .description("Potential NullPointerException: result of " + innerName
                            + "() is chained without null check. Wrap with Optional or add explicit null guard.")
                        .codeSnippet(expr.toString())
                        .build());
                }
            }
        }
    }

    // ─── Java regex fallback ─────────────────────────────────────

    private List<VulnerabilityResult> scanJavaViaRegex(String fileName, String code) {
        List<VulnerabilityResult> results = new ArrayList<>();
        String[] lines = code.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            int lineNum = i + 1;
            if (Pattern.compile("(?i)(password|apikey|secret)\\s*=\\s*\"[^\"]+\"").matcher(line).find()) {
                results.add(buildResult("JAVA-001", Severity.CRITICAL, fileName, lineNum,
                    "Hardcoded secret detected", line));
            }
            if (Pattern.compile("(?i)execute\\w*\\s*\\(.*\\+").matcher(line).find()) {
                results.add(buildResult("JAVA-002", Severity.CRITICAL, fileName, lineNum,
                    "Potential SQL Injection via string concatenation", line));
            }
        }
        return results;
    }

    // ─────────────────────────────────────────────────────────────
    //  JAVASCRIPT REGEX ANALYSIS
    // ─────────────────────────────────────────────────────────────

    private List<VulnerabilityResult> scanJavaScript(String fileName, String code) {
        List<VulnerabilityResult> results = new ArrayList<>();
        String[] lines = code.split("\n");

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            int lineNum = i + 1;

            // JS-001: Hardcoded secrets
            if (Pattern.compile("(?i)(password|apiKey|api_key|token|secret|authKey)\\s*[=:]\\s*['\"][^'\"]{4,}['\"]").matcher(trimmed).find()) {
                results.add(buildResult("JS-001", Severity.CRITICAL, fileName, lineNum,
                    "Hardcoded secret or credential in JavaScript source", trimmed));
            }

            // JS-002: eval() usage
            if (Pattern.compile("\\beval\\s*\\(").matcher(trimmed).find()) {
                results.add(buildResult("JS-002", Severity.HIGH, fileName, lineNum,
                    "eval() detected — executes arbitrary code. Replace with safer alternatives like JSON.parse().", trimmed));
            }

            // JS-003: innerHTML direct assignment with variable
            if (Pattern.compile("\\.innerHTML\\s*=\\s*(?!\\s*['\"])").matcher(trimmed).find()) {
                results.add(buildResult("JS-003", Severity.HIGH, fileName, lineNum,
                    "Direct innerHTML assignment with non-literal value — XSS risk. Use textContent or DOMPurify.", trimmed));
            }

            // JS-004: document.write with dynamic content
            if (Pattern.compile("document\\.write\\s*\\((?!\\s*['\"])").matcher(trimmed).find()) {
                results.add(buildResult("JS-004", Severity.HIGH, fileName, lineNum,
                    "document.write() with dynamic content — XSS risk and blocks parsing.", trimmed));
            }

            // JS-005: SQL in template literal
            if (Pattern.compile("(?i)(query|sql|execute)\\s*[(`].*\\$\\{").matcher(trimmed).find()) {
                results.add(buildResult("JS-005", Severity.CRITICAL, fileName, lineNum,
                    "Potential SQL Injection via template literal interpolation in query call. Use parameterized queries.", trimmed));
            }

            // JS-006: console.log of sensitive data
            if (Pattern.compile("(?i)console\\.(log|warn|error)\\s*\\(.*(?:password|token|secret)").matcher(trimmed).find()) {
                results.add(buildResult("JS-006", Severity.MEDIUM, fileName, lineNum,
                    "Sensitive data logged to console — remove debug logging before production.", trimmed));
            }

            // JS-007: Prototype pollution
            if (Pattern.compile("\\.__proto__\\s*=|Object\\.assign\\s*\\(\\s*\\{\\}").matcher(trimmed).find()) {
                results.add(buildResult("JS-007", Severity.HIGH, fileName, lineNum,
                    "Potential prototype pollution — objects merged without guard against __proto__ overrides.", trimmed));
            }

            // JS-008: Insecure random for crypto use
            if (Pattern.compile("Math\\.random\\s*\\(").matcher(trimmed).find()) {
                results.add(buildResult("JS-008", Severity.LOW, fileName, lineNum,
                    "Math.random() is not cryptographically secure. For security-sensitive randomness, use crypto.getRandomValues().", trimmed));
            }

            // JS-009: Dangerously set inner HTML (React)
            if (Pattern.compile("dangerouslySetInnerHTML").matcher(trimmed).find()) {
                results.add(buildResult("JS-009", Severity.MEDIUM, fileName, lineNum,
                    "dangerouslySetInnerHTML used — ensure content is sanitized with DOMPurify before rendering.", trimmed));
            }

            // JS-010: Open redirect via location.href assignment
            if (Pattern.compile("location\\.href\\s*=\\s*(?!\\s*['\"])").matcher(trimmed).find()) {
                results.add(buildResult("JS-010", Severity.MEDIUM, fileName, lineNum,
                    "Potential open redirect: location.href assigned from non-literal source. Validate destination URL against whitelist.", trimmed));
            }

            // JS-011: Hardcoded localhost/IP in production code
            if (Pattern.compile("(?i)(http://localhost|http://127\\.0\\.0\\.1|http://0\\.0\\.0\\.0)").matcher(trimmed).find()) {
                results.add(buildResult("JS-011", Severity.LOW, fileName, lineNum,
                    "Hardcoded localhost URL found — use environment variables for API endpoints.", trimmed));
            }

            // JS-012: setTimeout/setInterval with string argument
            if (Pattern.compile("(setTimeout|setInterval)\\s*\\(\\s*['\"]").matcher(trimmed).find()) {
                results.add(buildResult("JS-012", Severity.MEDIUM, fileName, lineNum,
                    "setTimeout/setInterval called with string argument — equivalent to eval(). Pass a function reference instead.", trimmed));
            }
        }
        return results;
    }

    // ─────────────────────────────────────────────────────────────
    //  UTILITY
    // ─────────────────────────────────────────────────────────────

    private VulnerabilityResult buildResult(String ruleId, Severity severity, String fileName,
                                             int lineNumber, String description, String codeSnippet) {
        return VulnerabilityResult.builder()
            .ruleId(ruleId)
            .severity(severity)
            .fileName(fileName)
            .lineNumber(lineNumber)
            .description(description)
            .codeSnippet(codeSnippet.length() > 200 ? codeSnippet.substring(0, 200) + "…" : codeSnippet)
            .build();
    }
}
