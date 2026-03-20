#!/usr/bin/env bash
# ─── Manual Scan E2E Test Cases ─────────────────────────────────────────────
# Tests the POST /api/scans/manual endpoint with vulnerable Java & JS samples.
# Requires: backend running on localhost:8080, user "Ashish" with password "ashish"
# Usage: bash test-manual-scan.sh
# ─────────────────────────────────────────────────────────────────────────────

BASE="http://localhost:8080"
PASS=0
FAIL=0
TOTAL=0

green()  { printf "\e[32m%s\e[0m\n" "$1"; }
red()    { printf "\e[31m%s\e[0m\n" "$1"; }
yellow() { printf "\e[33m%s\e[0m\n" "$1"; }

assert() {
  TOTAL=$((TOTAL + 1))
  local desc="$1" actual="$2" expected="$3"
  if echo "$actual" | grep -q "$expected"; then
    green "  PASS: $desc"
    PASS=$((PASS + 1))
  else
    red "  FAIL: $desc"
    red "    expected to contain: $expected"
    red "    got: $(echo "$actual" | head -c 200)"
    FAIL=$((FAIL + 1))
  fi
}

assert_count() {
  TOTAL=$((TOTAL + 1))
  local desc="$1" actual="$2" operator="$3" expected="$4"
  if [ "$operator" = ">=" ] && [ "$actual" -ge "$expected" ] 2>/dev/null; then
    green "  PASS: $desc (got $actual)"
    PASS=$((PASS + 1))
  elif [ "$operator" = "=" ] && [ "$actual" -eq "$expected" ] 2>/dev/null; then
    green "  PASS: $desc (got $actual)"
    PASS=$((PASS + 1))
  else
    red "  FAIL: $desc (expected $operator $expected, got $actual)"
    FAIL=$((FAIL + 1))
  fi
}

# ─── Login ───────────────────────────────────────────────────────────────────
echo ""
yellow "=== Logging in as Ashish ==="
LOGIN=$(curl -s -X POST "$BASE/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"Ashish","password":"ashish"}')

TOKEN=$(echo "$LOGIN" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
if [ -z "$TOKEN" ]; then
  red "Login failed! Response: $LOGIN"
  red "Trying admin account..."
  LOGIN=$(curl -s -X POST "$BASE/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}')
  TOKEN=$(echo "$LOGIN" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
  if [ -z "$TOKEN" ]; then
    red "Both logins failed. Is the backend running?"
    exit 1
  fi
  green "Logged in as admin"
else
  green "Logged in as Ashish"
fi

AUTH="Authorization: Bearer $TOKEN"

# ═══════════════════════════════════════════════════════════════════════════════
#  TEST CASE 1: Java Vulnerable Code
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
yellow "=== TEST CASE 1: Java Vulnerable Code ==="
yellow "  Submitting Java code with: hardcoded secrets, SQL injection,"
yellow "  Runtime.exec(), weak crypto (MD5), and string concat in queries"

JAVA_CODE='public class UserService {
    String dbPassword = "super_secret_123";
    String apiKey = "AKIAIOSFODNN7EXAMPLE";

    public void getUser(String userId) {
        String query = "SELECT * FROM users WHERE id = " + userId;
        conn.executeQuery(query);
    }

    public void runCommand(String input) {
        Runtime.getRuntime().exec("sh -c " + input);
    }

    public void hashPassword(String pw) {
        MessageDigest md = MessageDigest.getInstance("MD5");
    }
}'

JAVA_RESULT=$(curl -s -X POST "$BASE/api/scans/manual" \
  -H "Content-Type: application/json" \
  -H "$AUTH" \
  -d "$(cat <<EOF
{
  "fileName": "VulnerableService.java",
  "code": $(echo "$JAVA_CODE" | python3 -c "import sys,json; print(json.dumps(sys.stdin.read()))"),
  "repoName": "java-test"
}
EOF
)")

echo ""
yellow "  --- Results ---"

# 1.1 Scan should FAIL
JAVA_STATUS=$(echo "$JAVA_RESULT" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
assert "1.1 Java scan status is FAIL" "$JAVA_STATUS" "FAIL"

# 1.2 Should detect multiple vulnerabilities
JAVA_VULN_COUNT=$(echo "$JAVA_RESULT" | grep -o '"totalVulnerabilities":[0-9]*' | cut -d: -f2)
assert_count "1.2 Java scan found multiple vulnerabilities" "$JAVA_VULN_COUNT" ">=" 4

# 1.3 Should detect hardcoded secret (JAVA-001)
assert "1.3 Detected hardcoded secret (JAVA-001)" "$JAVA_RESULT" "JAVA-001"

# 1.4 Should detect SQL injection (JAVA-002)
assert "1.4 Detected SQL injection (JAVA-002)" "$JAVA_RESULT" "JAVA-002"

# 1.5 Should detect Runtime.exec (JAVA-004a)
assert "1.5 Detected Runtime.exec command injection (JAVA-004a)" "$JAVA_RESULT" "JAVA-004a"

# 1.6 Should detect weak crypto MD5 (JAVA-006)
assert "1.6 Detected weak cryptography MD5 (JAVA-006)" "$JAVA_RESULT" "JAVA-006"

# 1.7 Should have a scanId
JAVA_SCAN_ID=$(echo "$JAVA_RESULT" | grep -o '"scanId":[0-9]*' | cut -d: -f2)
assert "1.7 Scan was persisted with an ID" "$JAVA_SCAN_ID" "[0-9]"

# 1.8 Verify severity levels exist
assert "1.8 Contains CRITICAL severity" "$JAVA_RESULT" "CRITICAL"
assert "1.9 Contains HIGH severity" "$JAVA_RESULT" "HIGH"

# 1.10 Verify scan is retrievable via GET
if [ -n "$JAVA_SCAN_ID" ]; then
  JAVA_GET=$(curl -s -H "$AUTH" "$BASE/api/scans/$JAVA_SCAN_ID")
  assert "1.10 Persisted scan retrievable via GET /api/scans/$JAVA_SCAN_ID" "$JAVA_GET" "VulnerableService.java"
fi

# ═══════════════════════════════════════════════════════════════════════════════
#  TEST CASE 2: JavaScript Vulnerable Code
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
yellow "=== TEST CASE 2: JavaScript Vulnerable Code ==="
yellow "  Submitting JS code with: hardcoded secrets, eval(), innerHTML XSS,"
yellow "  SQL in template literal, console.log secrets, setTimeout string"

JS_CODE='const apiKey = "sk-live-abcdef123456";
const token = "ghp_xxxxxxxxxxxxxxxxxxxx";

function search(userInput) {
  const sql = `SELECT * FROM items WHERE name = ${userInput}`;
  db.query(sql);
  eval(userInput);
  document.getElementById("out").innerHTML = userInput;
  console.log("Debug token:", token);
  setTimeout("alert(hi)", 1000);
}'

JS_RESULT=$(curl -s -X POST "$BASE/api/scans/manual" \
  -H "Content-Type: application/json" \
  -H "$AUTH" \
  -d "$(cat <<EOF
{
  "fileName": "vulnerable-app.js",
  "code": $(echo "$JS_CODE" | python3 -c "import sys,json; print(json.dumps(sys.stdin.read()))"),
  "repoName": "js-test"
}
EOF
)")

echo ""
yellow "  --- Results ---"

# 2.1 Scan should FAIL
JS_STATUS=$(echo "$JS_RESULT" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
assert "2.1 JS scan status is FAIL" "$JS_STATUS" "FAIL"

# 2.2 Should detect multiple vulnerabilities
JS_VULN_COUNT=$(echo "$JS_RESULT" | grep -o '"totalVulnerabilities":[0-9]*' | cut -d: -f2)
assert_count "2.2 JS scan found multiple vulnerabilities" "$JS_VULN_COUNT" ">=" 5

# 2.3 Should detect hardcoded secrets (JS-001)
assert "2.3 Detected hardcoded secret (JS-001)" "$JS_RESULT" "JS-001"

# 2.4 Should detect eval() (JS-002)
assert "2.4 Detected eval() usage (JS-002)" "$JS_RESULT" "JS-002"

# 2.5 Should detect innerHTML XSS (JS-003)
assert "2.5 Detected innerHTML XSS (JS-003)" "$JS_RESULT" "JS-003"

# 2.6 Should detect SQL in template literal (JS-005)
assert "2.6 Detected SQL injection in template literal (JS-005)" "$JS_RESULT" "JS-005"

# 2.7 Should detect console.log of secrets (JS-006)
assert "2.7 Detected console.log of sensitive data (JS-006)" "$JS_RESULT" "JS-006"

# 2.8 Should detect setTimeout with string (JS-012)
assert "2.8 Detected setTimeout with string arg (JS-012)" "$JS_RESULT" "JS-012"

# 2.9 Should have a scanId
JS_SCAN_ID=$(echo "$JS_RESULT" | grep -o '"scanId":[0-9]*' | cut -d: -f2)
assert "2.9 Scan was persisted with an ID" "$JS_SCAN_ID" "[0-9]"

# 2.10 Verify scan is retrievable via GET
if [ -n "$JS_SCAN_ID" ]; then
  JS_GET=$(curl -s -H "$AUTH" "$BASE/api/scans/$JS_SCAN_ID")
  assert "2.10 Persisted scan retrievable via GET /api/scans/$JS_SCAN_ID" "$JS_GET" "vulnerable-app.js"
fi

# ═══════════════════════════════════════════════════════════════════════════════
#  TEST CASE 3: Clean Code (no vulnerabilities)
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
yellow "=== TEST CASE 3: Clean Java Code (should PASS) ==="

CLEAN_CODE='public class SafeService {
    private final DataSource dataSource;

    public SafeService(DataSource ds) {
        this.dataSource = ds;
    }

    public User getUser(Long id) {
        PreparedStatement ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
        ps.setLong(1, id);
        return ps.executeQuery();
    }
}'

CLEAN_RESULT=$(curl -s -X POST "$BASE/api/scans/manual" \
  -H "Content-Type: application/json" \
  -H "$AUTH" \
  -d "$(cat <<EOF
{
  "fileName": "SafeService.java",
  "code": $(echo "$CLEAN_CODE" | python3 -c "import sys,json; print(json.dumps(sys.stdin.read()))"),
  "repoName": "clean-test"
}
EOF
)")

echo ""
yellow "  --- Results ---"

CLEAN_STATUS=$(echo "$CLEAN_RESULT" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
assert "3.1 Clean code scan status is PASS" "$CLEAN_STATUS" "PASS"

CLEAN_VULN_COUNT=$(echo "$CLEAN_RESULT" | grep -o '"totalVulnerabilities":[0-9]*' | cut -d: -f2)
assert_count "3.2 Clean code has 0 vulnerabilities" "$CLEAN_VULN_COUNT" "=" 0

# ═══════════════════════════════════════════════════════════════════════════════
#  TEST CASE 4: Notifications generated
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
yellow "=== TEST CASE 4: Notifications ==="

USER_ID=$(echo "$LOGIN" | grep -o '"userId":"[^"]*"' | cut -d'"' -f4)
if [ -z "$USER_ID" ]; then
  USER_ID=$(echo "$LOGIN" | grep -o '"userId":[0-9]*' | cut -d: -f2)
fi

if [ -n "$USER_ID" ]; then
  NOTIFS=$(curl -s -H "$AUTH" "$BASE/api/notifications/$USER_ID")
  NOTIF_COUNT=$(echo "$NOTIFS" | grep -o '"id"' | wc -l)
  assert_count "4.1 Notifications were created (at least 3 scans done)" "$NOTIF_COUNT" ">=" 3
  assert "4.2 Notification mentions vulnerability count or clean push" "$NOTIFS" "vulnerabilit\|Clean push"
else
  red "  SKIP: Could not determine userId for notification check"
fi

# ═══════════════════════════════════════════════════════════════════════════════
#  TEST CASE 5: Unsupported file type
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
yellow "=== TEST CASE 5: Unsupported File Type (.py) ==="

PY_RESULT=$(curl -s -X POST "$BASE/api/scans/manual" \
  -H "Content-Type: application/json" \
  -H "$AUTH" \
  -d '{"fileName":"script.py","code":"password = \"hunter2\"","repoName":"py-test"}')

PY_STATUS=$(echo "$PY_RESULT" | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
assert "5.1 Unsupported file type results in PASS (no scanner)" "$PY_STATUS" "PASS"

PY_VULN_COUNT=$(echo "$PY_RESULT" | grep -o '"totalVulnerabilities":[0-9]*' | cut -d: -f2)
assert_count "5.2 Unsupported file type has 0 vulnerabilities" "$PY_VULN_COUNT" "=" 0

# ═══════════════════════════════════════════════════════════════════════════════
#  TEST CASE 6: Unauthenticated access denied
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
yellow "=== TEST CASE 6: Auth Required ==="

UNAUTH=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE/api/scans/manual" \
  -H "Content-Type: application/json" \
  -d '{"fileName":"test.java","code":"int x = 1;","repoName":"test"}')
assert "6.1 Unauthenticated request returns 401/403" "$UNAUTH" "40[13]"

# ═══════════════════════════════════════════════════════════════════════════════
#  SUMMARY
# ═══════════════════════════════════════════════════════════════════════════════
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
if [ "$FAIL" -eq 0 ]; then
  green "  ALL $TOTAL TESTS PASSED"
else
  red "  $FAIL/$TOTAL TESTS FAILED"
  green "  $PASS/$TOTAL tests passed"
fi
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
exit $FAIL
