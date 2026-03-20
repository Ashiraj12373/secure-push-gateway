package com.securepushgateway.engine;

import com.securepushgateway.model.VulnerabilityResult;
import com.securepushgateway.model.VulnerabilityResult.Severity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ScanEngineService.
 * Covers all 5 vulnerability classes for both Java and JavaScript.
 */
class ScanEngineServiceTest {

    private ScanEngineService engine;

    @BeforeEach
    void setUp() {
        engine = new ScanEngineService();
    }

    // ─────────────────────────────────────────────────────────────
    //  JAVA TESTS
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Java — Hardcoded Secrets (JAVA-001)")
    class JavaHardcodedSecrets {

        @Test
        @DisplayName("Detects hardcoded password in variable assignment")
        void detectsHardcodedPassword() {
            String code = """
                public class Config {
                    String password = "supersecret123";
                }
                """;
            List<VulnerabilityResult> results = engine.scan("Config.java", code);
            assertThat(results).anyMatch(r ->
                r.getRuleId().equals("JAVA-001") && r.getSeverity() == Severity.CRITICAL);
        }

        @Test
        @DisplayName("Detects hardcoded API key")
        void detectsHardcodedApiKey() {
            String code = """
                public class ApiClient {
                    private static final String apiKey = "sk-abc123xyz456";
                }
                """;
            List<VulnerabilityResult> results = engine.scan("ApiClient.java", code);
            assertThat(results).anyMatch(r -> r.getRuleId().equals("JAVA-001"));
        }

        @Test
        @DisplayName("Does NOT flag non-secret string assignments")
        void doesNotFlagNonSecrets() {
            String code = """
                public class App {
                    private String appName = "MyApp";
                }
                """;
            List<VulnerabilityResult> results = engine.scan("App.java", code);
            assertThat(results).noneMatch(r -> r.getRuleId().equals("JAVA-001"));
        }
    }

    @Nested
    @DisplayName("Java — SQL Injection (JAVA-002)")
    class JavaSqlInjection {

        @Test
        @DisplayName("Detects string concatenation in executeQuery")
        void detectsSqlInjection() {
            String code = """
                import java.sql.*;
                public class UserDao {
                    public void getUser(Connection conn, String id) throws SQLException {
                        ResultSet rs = conn.createStatement().executeQuery("SELECT * FROM users WHERE id = " + id);
                    }
                }
                """;
            List<VulnerabilityResult> results = engine.scan("UserDao.java", code);
            assertThat(results).anyMatch(r ->
                r.getRuleId().equals("JAVA-002") && r.getSeverity() == Severity.CRITICAL);
        }

        @Test
        @DisplayName("Does NOT flag parameterized PreparedStatement")
        void doesNotFlagPreparedStatement() {
            String code = """
                import java.sql.*;
                public class UserDao {
                    public void getUser(Connection conn, String id) throws SQLException {
                        PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
                        ps.setString(1, id);
                    }
                }
                """;
            List<VulnerabilityResult> results = engine.scan("UserDao.java", code);
            assertThat(results).noneMatch(r -> r.getRuleId().equals("JAVA-002"));
        }
    }

    @Nested
    @DisplayName("Java — Unsafe Deserialization (JAVA-003)")
    class JavaUnsafeDeserialization {

        @Test
        @DisplayName("Detects ObjectInputStream.readObject()")
        void detectsUnsafeDeserialization() {
            String code = """
                import java.io.*;
                public class Deserializer {
                    public Object deserialize(byte[] data) throws Exception {
                        ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data));
                        return ois.readObject();
                    }
                }
                """;
            List<VulnerabilityResult> results = engine.scan("Deserializer.java", code);
            assertThat(results).anyMatch(r ->
                r.getRuleId().equals("JAVA-003") && r.getSeverity() == Severity.HIGH);
        }
    }

    @Nested
    @DisplayName("Java — Dangerous Methods (JAVA-004)")
    class JavaDangerousMethods {

        @Test
        @DisplayName("Detects Runtime.exec()")
        void detectsRuntimeExec() {
            String code = """
                public class CommandRunner {
                    public void run(String cmd) throws Exception {
                        Runtime.getRuntime().exec(cmd);
                    }
                }
                """;
            List<VulnerabilityResult> results = engine.scan("CommandRunner.java", code);
            assertThat(results).anyMatch(r -> r.getRuleId().equals("JAVA-004a"));
        }

        @Test
        @DisplayName("Detects System.exit()")
        void detectsSystemExit() {
            String code = """
                public class App {
                    public void shutdown() {
                        System.exit(0);
                    }
                }
                """;
            List<VulnerabilityResult> results = engine.scan("App.java", code);
            assertThat(results).anyMatch(r -> r.getRuleId().equals("JAVA-004b"));
        }
    }

    @Nested
    @DisplayName("Java — XSS Vectors (JAVA-005)")
    class JavaXss {

        @Test
        @DisplayName("Detects unescaped variable in response writer")
        void detectsXss() {
            String code = """
                import jakarta.servlet.http.*;
                public class OutputServlet extends HttpServlet {
                    protected void doGet(HttpServletRequest req, HttpServletResponse response) throws Exception {
                        String name = req.getParameter("name");
                        response.getWriter().println(name);
                    }
                }
                """;
            List<VulnerabilityResult> results = engine.scan("OutputServlet.java", code);
            assertThat(results).anyMatch(r ->
                r.getRuleId().equals("JAVA-005") && r.getSeverity() == Severity.HIGH);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  JAVASCRIPT TESTS
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("JavaScript — Hardcoded Secrets (JS-001)")
    class JsHardcodedSecrets {

        @Test
        @DisplayName("Detects hardcoded apiKey in JS")
        void detectsApiKey() {
            String code = "const apiKey = 'sk-abc123456789';";
            List<VulnerabilityResult> results = engine.scan("config.js", code);
            assertThat(results).anyMatch(r ->
                r.getRuleId().equals("JS-001") && r.getSeverity() == Severity.CRITICAL);
        }
    }

    @Nested
    @DisplayName("JavaScript — eval() Usage (JS-002)")
    class JsEval {

        @Test
        @DisplayName("Detects eval() call")
        void detectsEval() {
            String code = "eval(userInput);";
            List<VulnerabilityResult> results = engine.scan("script.js", code);
            assertThat(results).anyMatch(r ->
                r.getRuleId().equals("JS-002") && r.getSeverity() == Severity.HIGH);
        }
    }

    @Nested
    @DisplayName("JavaScript — innerHTML XSS (JS-003)")
    class JsInnerHtml {

        @Test
        @DisplayName("Detects direct innerHTML assignment with variable")
        void detectsInnerHtml() {
            String code = "element.innerHTML = userContent;";
            List<VulnerabilityResult> results = engine.scan("render.js", code);
            assertThat(results).anyMatch(r ->
                r.getRuleId().equals("JS-003") && r.getSeverity() == Severity.HIGH);
        }

        @Test
        @DisplayName("Does NOT flag literal innerHTML assignment")
        void doesNotFlagLiteralInnerHtml() {
            String code = "element.innerHTML = '<p>Hello</p>';";
            List<VulnerabilityResult> results = engine.scan("render.js", code);
            assertThat(results).noneMatch(r -> r.getRuleId().equals("JS-003"));
        }
    }

    @Nested
    @DisplayName("JavaScript — SQL Injection (JS-005)")
    class JsSqlInjection {

        @Test
        @DisplayName("Detects template literal interpolation in query")
        void detectsSqlInjection() {
            String code = "const result = await db.query(`SELECT * FROM users WHERE id = ${userId}`);";
            List<VulnerabilityResult> results = engine.scan("db.js", code);
            assertThat(results).anyMatch(r ->
                r.getRuleId().equals("JS-005") && r.getSeverity() == Severity.CRITICAL);
        }
    }

    @Nested
    @DisplayName("JavaScript — document.write() (JS-004)")
    class JsDocumentWrite {

        @Test
        @DisplayName("Detects document.write with dynamic content")
        void detectsDocumentWrite() {
            String code = "document.write(userData);";
            List<VulnerabilityResult> results = engine.scan("page.js", code);
            assertThat(results).anyMatch(r ->
                r.getRuleId().equals("JS-004") && r.getSeverity() == Severity.HIGH);
        }
    }

    // ─────────────────────────────────────────────────────────────
    //  CLEAN CODE — SHOULD PRODUCE NO RESULTS
    // ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Clean code — no vulnerabilities")
    class CleanCode {

        @Test
        @DisplayName("Clean Java file produces no vulnerabilities")
        void cleanJavaFile() {
            String code = """
                import java.sql.*;
                public class SafeDao {
                    public User getUser(Connection conn, String id) throws SQLException {
                        PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
                        ps.setString(1, id);
                        ResultSet rs = ps.executeQuery();
                        return rs.next() ? mapUser(rs) : null;
                    }
                }
                """;
            List<VulnerabilityResult> results = engine.scan("SafeDao.java", code);
            assertThat(results).isEmpty();
        }

        @Test
        @DisplayName("Clean JS file produces no vulnerabilities")
        void cleanJsFile() {
            String code = """
                const userId = sanitize(req.params.id);
                const result = await db.query('SELECT * FROM users WHERE id = $1', [userId]);
                element.textContent = safeContent;
                """;
            List<VulnerabilityResult> results = engine.scan("safe.js", code);
            assertThat(results).isEmpty();
        }
    }
}
