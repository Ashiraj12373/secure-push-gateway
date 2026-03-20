#!/bin/bash
# ═══════════════════════════════════════════════════════════════
#  Secure Push Gateway — End-to-End API Test Suite
# ═══════════════════════════════════════════════════════════════

BASE="http://localhost:8080"
PASS=0
FAIL=0
TOTAL=0

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

assert() {
  TOTAL=$((TOTAL + 1))
  local test_name="$1"
  local expected="$2"
  local actual="$3"
  if echo "$actual" | grep -q "$expected"; then
    PASS=$((PASS + 1))
    echo -e "  ${GREEN}PASS${NC}  $test_name"
  else
    FAIL=$((FAIL + 1))
    echo -e "  ${RED}FAIL${NC}  $test_name"
    echo -e "        Expected: ${YELLOW}$expected${NC}"
    echo -e "        Got:      ${YELLOW}$(echo "$actual" | head -c 200)${NC}"
  fi
}

assert_status() {
  TOTAL=$((TOTAL + 1))
  local test_name="$1"
  local expected_code="$2"
  local actual_code="$3"
  if [ "$actual_code" = "$expected_code" ]; then
    PASS=$((PASS + 1))
    echo -e "  ${GREEN}PASS${NC}  $test_name (HTTP $actual_code)"
  else
    FAIL=$((FAIL + 1))
    echo -e "  ${RED}FAIL${NC}  $test_name (expected HTTP $expected_code, got $actual_code)"
  fi
}

section() {
  echo ""
  echo -e "${CYAN}━━━ $1 ━━━${NC}"
}

# ═══════════════════════════════════════════════════════════════
section "1. AUTHENTICATION"
# ═══════════════════════════════════════════════════════════════

# 1.1 Owner login
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}')
BODY=$(echo "$RESP" | head -n -1)
CODE=$(echo "$RESP" | tail -1)
assert_status "1.1 Owner login returns 200" "200" "$CODE"
assert "1.1 Owner login returns token" "token" "$BODY"
assert "1.1 Owner login returns OWNER role" "OWNER" "$BODY"
ADMIN_TOKEN=$(echo "$BODY" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
ADMIN_ID=$(echo "$BODY" | grep -o '"userId":"[^"]*"' | cut -d'"' -f4)

# 1.2 Developer login (Ashish)
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"Ashish","password":"ashish"}')
BODY=$(echo "$RESP" | head -n -1)
CODE=$(echo "$RESP" | tail -1)
assert_status "1.2 Developer login returns 200" "200" "$CODE"
assert "1.2 Developer login returns DEVELOPER role" "DEVELOPER" "$BODY"
DEV_TOKEN=$(echo "$BODY" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
DEV_ID=$(echo "$BODY" | grep -o '"userId":"[^"]*"' | cut -d'"' -f4)

# 1.3 Invalid login
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"wrongpassword"}')
CODE=$(echo "$RESP" | tail -1)
assert_status "1.3 Invalid login returns 403" "403" "$CODE"

# 1.4 Duplicate registration
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","email":"dup@test.com","password":"test123"}')
BODY=$(echo "$RESP" | head -n -1)
CODE=$(echo "$RESP" | tail -1)
assert_status "1.4 Duplicate registration returns 400" "400" "$CODE"
assert "1.4 Duplicate registration error message" "already taken" "$BODY"

# 1.5 Get current user (/me)
RESP=$(curl -s -w "\n%{http_code}" -X GET "$BASE/api/auth/me" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
BODY=$(echo "$RESP" | head -n -1)
CODE=$(echo "$RESP" | tail -1)
assert_status "1.5 /me returns 200" "200" "$CODE"
assert "1.5 /me returns admin username" "admin" "$BODY"

# 1.6 Unauthenticated access blocked
RESP=$(curl -s -w "\n%{http_code}" -X GET "$BASE/api/scans")
CODE=$(echo "$RESP" | tail -1)
assert_status "1.6 Unauthenticated access returns 401/403" "403" "$CODE"

# ═══════════════════════════════════════════════════════════════
section "2. GITHUB WEBHOOK"
# ═══════════════════════════════════════════════════════════════

# 2.1 Webhook accepts unsigned request in dev mode (empty secret = skip HMAC)
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/webhook/github" \
  -H "Content-Type: application/json" \
  -H "X-GitHub-Event: push" \
  -d '{"ref":"refs/heads/main","pusher":{"name":"testuser"},"repository":{"full_name":"test/repo"},"commits":[]}')
BODY=$(echo "$RESP" | head -n -1)
CODE=$(echo "$RESP" | tail -1)
assert_status "2.1 Webhook (dev mode, no secret) returns 200" "200" "$CODE"
assert "2.1 Webhook accepted" "Accepted" "$BODY"

# 2.2 Webhook ignores non-push events
# Compute HMAC for empty-secret
WEBHOOK_SECRET=""
PAYLOAD='{"ref":"refs/heads/main"}'
if [ -z "$WEBHOOK_SECRET" ]; then
  SIG="sha256=$(echo -n "$PAYLOAD" | openssl dgst -sha256 -hmac "" | awk '{print $NF}')"
else
  SIG="sha256=$(echo -n "$PAYLOAD" | openssl dgst -sha256 -hmac "$WEBHOOK_SECRET" | awk '{print $NF}')"
fi
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/webhook/github" \
  -H "Content-Type: application/json" \
  -H "X-GitHub-Event: ping" \
  -H "X-Hub-Signature-256: $SIG" \
  -d "$PAYLOAD")
BODY=$(echo "$RESP" | head -n -1)
CODE=$(echo "$RESP" | tail -1)
assert_status "2.2 Non-push event returns 200" "200" "$CODE"
assert "2.2 Non-push event ignored" "Event ignored" "$BODY"

# 2.3 Valid push webhook (triggers async scan)
PUSH_PAYLOAD='{"ref":"refs/heads/main","pusher":{"name":"Ashish"},"repository":{"full_name":"Ashish/test-repo"},"commits":[{"id":"abcdef1234567890abcdef1234567890abcdef12","message":"test commit","added":[],"modified":["TestFile.java"],"removed":[]}]}'
if [ -z "$WEBHOOK_SECRET" ]; then
  PUSH_SIG="sha256=$(echo -n "$PUSH_PAYLOAD" | openssl dgst -sha256 -hmac "" | awk '{print $NF}')"
else
  PUSH_SIG="sha256=$(echo -n "$PUSH_PAYLOAD" | openssl dgst -sha256 -hmac "$WEBHOOK_SECRET" | awk '{print $NF}')"
fi
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/webhook/github" \
  -H "Content-Type: application/json" \
  -H "X-GitHub-Event: push" \
  -H "X-Hub-Signature-256: $PUSH_SIG" \
  -d "$PUSH_PAYLOAD")
BODY=$(echo "$RESP" | head -n -1)
CODE=$(echo "$RESP" | tail -1)
assert_status "2.3 Valid push webhook returns 200" "200" "$CODE"
assert "2.3 Valid push webhook accepted" "Accepted" "$BODY"

# Wait for async scan to complete
echo -e "  ${YELLOW}...waiting 3s for async scan...${NC}"
sleep 3

# ═══════════════════════════════════════════════════════════════
section "3. SCANS API"
# ═══════════════════════════════════════════════════════════════

# 3.1 Owner can list all scans
RESP=$(curl -s -w "\n%{http_code}" -X GET "$BASE/api/scans" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
BODY=$(echo "$RESP" | head -n -1)
CODE=$(echo "$RESP" | tail -1)
assert_status "3.1 Owner list scans returns 200" "200" "$CODE"

# 3.2 Developer can list their scans
RESP=$(curl -s -w "\n%{http_code}" -X GET "$BASE/api/scans" \
  -H "Authorization: Bearer $DEV_TOKEN")
BODY=$(echo "$RESP" | head -n -1)
CODE=$(echo "$RESP" | tail -1)
assert_status "3.2 Developer list scans returns 200" "200" "$CODE"

# 3.3 Owner stats endpoint
RESP=$(curl -s -w "\n%{http_code}" -X GET "$BASE/api/scans/stats" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
BODY=$(echo "$RESP" | head -n -1)
CODE=$(echo "$RESP" | tail -1)
assert_status "3.3 Owner stats returns 200" "200" "$CODE"
assert "3.3 Stats has total field" "total" "$BODY"
assert "3.3 Stats has passRate field" "passRate" "$BODY"

# 3.4 Get scans by developer
RESP=$(curl -s -w "\n%{http_code}" -X GET "$BASE/api/scans/developer/$DEV_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
CODE=$(echo "$RESP" | tail -1)
assert_status "3.4 Scans by developer returns 200" "200" "$CODE"

# 3.5 Get scan detail (if any scans exist)
# First grab a scan ID from the list
SCAN_LIST=$(curl -s -X GET "$BASE/api/scans" -H "Authorization: Bearer $ADMIN_TOKEN")
SCAN_ID=$(echo "$SCAN_LIST" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
if [ -n "$SCAN_ID" ]; then
  RESP=$(curl -s -w "\n%{http_code}" -X GET "$BASE/api/scans/$SCAN_ID" \
    -H "Authorization: Bearer $ADMIN_TOKEN")
  BODY=$(echo "$RESP" | head -n -1)
  CODE=$(echo "$RESP" | tail -1)
  assert_status "3.5 Scan detail returns 200" "200" "$CODE"
  assert "3.5 Scan detail has repoName" "repoName" "$BODY"
  assert "3.5 Scan detail has vulnerabilities" "vulnerabilities" "$BODY"
  assert "3.5 Scan detail has status" "status" "$BODY"
else
  echo -e "  ${YELLOW}SKIP${NC}  3.5 Scan detail (no scans in DB yet)"
fi

# 3.6 Scan not found
RESP=$(curl -s -w "\n%{http_code}" -X GET "$BASE/api/scans/99999" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
CODE=$(echo "$RESP" | tail -1)
assert_status "3.6 Non-existent scan returns 404" "404" "$CODE"

# ═══════════════════════════════════════════════════════════════
section "4. ROLE-BASED ACCESS CONTROL"
# ═══════════════════════════════════════════════════════════════

# 4.1 Developer cannot access owner-only stats
RESP=$(curl -s -w "\n%{http_code}" -X GET "$BASE/api/scans/stats" \
  -H "Authorization: Bearer $DEV_TOKEN")
CODE=$(echo "$RESP" | tail -1)
assert_status "4.1 Developer blocked from /scans/stats" "403" "$CODE"

# 4.2 Developer cannot list all developers
RESP=$(curl -s -w "\n%{http_code}" -X GET "$BASE/api/developers" \
  -H "Authorization: Bearer $DEV_TOKEN")
CODE=$(echo "$RESP" | tail -1)
assert_status "4.2 Developer blocked from /developers" "403" "$CODE"

# 4.3 Owner can list developers
RESP=$(curl -s -w "\n%{http_code}" -X GET "$BASE/api/developers" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
BODY=$(echo "$RESP" | head -n -1)
CODE=$(echo "$RESP" | tail -1)
assert_status "4.3 Owner can list developers" "200" "$CODE"
assert "4.3 Developer list includes Ashish" "Ashish" "$BODY"

# 4.4 Owner can get developer detail
RESP=$(curl -s -w "\n%{http_code}" -X GET "$BASE/api/developers/$DEV_ID" \
  -H "Authorization: Bearer $ADMIN_TOKEN")
BODY=$(echo "$RESP" | head -n -1)
CODE=$(echo "$RESP" | tail -1)
assert_status "4.4 Owner get developer detail" "200" "$CODE"
assert "4.4 Developer detail has username" "Ashish" "$BODY"

# ═══════════════════════════════════════════════════════════════
section "5. NOTIFICATIONS"
# ═══════════════════════════════════════════════════════════════

# 5.1 Get notifications for developer
RESP=$(curl -s -w "\n%{http_code}" -X GET "$BASE/api/notifications/$DEV_ID" \
  -H "Authorization: Bearer $DEV_TOKEN")
BODY=$(echo "$RESP" | head -n -1)
CODE=$(echo "$RESP" | tail -1)
assert_status "5.1 Get notifications returns 200" "200" "$CODE"

# 5.2 Check if scan notification was created (from webhook test)
NOTIF_ID=$(echo "$BODY" | grep -o '"id":[0-9]*' | head -1 | cut -d: -f2)
if [ -n "$NOTIF_ID" ]; then
  assert "5.2 Scan notification exists" "id" "$BODY"

  # 5.3 Mark notification as read
  RESP=$(curl -s -w "\n%{http_code}" -X PUT "$BASE/api/notifications/$NOTIF_ID/read" \
    -H "Authorization: Bearer $DEV_TOKEN")
  CODE=$(echo "$RESP" | tail -1)
  assert_status "5.3 Mark notification read returns 200" "200" "$CODE"

  # 5.4 Verify it's marked read
  RESP=$(curl -s -X GET "$BASE/api/notifications/$DEV_ID" \
    -H "Authorization: Bearer $DEV_TOKEN")
  assert "5.4 Notification marked as read" '"read":true' "$RESP"
else
  echo -e "  ${YELLOW}SKIP${NC}  5.2-5.4 (no notifications yet — webhook scan may not have created any)"
fi

# ═══════════════════════════════════════════════════════════════
section "6. BADGES"
# ═══════════════════════════════════════════════════════════════

# 6.1 Get badges for developer
RESP=$(curl -s -w "\n%{http_code}" -X GET "$BASE/api/badges/$DEV_ID" \
  -H "Authorization: Bearer $DEV_TOKEN")
BODY=$(echo "$RESP" | head -n -1)
CODE=$(echo "$RESP" | tail -1)
assert_status "6.1 Get badges returns 200" "200" "$CODE"

# 6.2 Manually evaluate badges
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/badges/evaluate/$DEV_ID" \
  -H "Authorization: Bearer $DEV_TOKEN")
CODE=$(echo "$RESP" | tail -1)
assert_status "6.2 Evaluate badges returns 200" "200" "$CODE"

# ═══════════════════════════════════════════════════════════════
section "7. SECURITY"
# ═══════════════════════════════════════════════════════════════

# 7.1 Expired/invalid JWT rejected
RESP=$(curl -s -w "\n%{http_code}" -X GET "$BASE/api/scans" \
  -H "Authorization: Bearer invalidtoken123")
CODE=$(echo "$RESP" | tail -1)
assert_status "7.1 Invalid JWT returns 403" "403" "$CODE"

# 7.2 No auth header rejected
RESP=$(curl -s -w "\n%{http_code}" -X GET "$BASE/api/auth/me")
CODE=$(echo "$RESP" | tail -1)
assert_status "7.2 No auth on /me returns 401/403" "403" "$CODE"

# 7.3 Auth endpoints are public (no token needed for login)
RESP=$(curl -s -w "\n%{http_code}" -X POST "$BASE/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}')
CODE=$(echo "$RESP" | tail -1)
assert_status "7.3 Login endpoint is public" "200" "$CODE"

# ═══════════════════════════════════════════════════════════════
section "8. CORS"
# ═══════════════════════════════════════════════════════════════

# 8.1 CORS preflight from allowed origin
RESP=$(curl -s -w "\n%{http_code}" -X OPTIONS "$BASE/api/auth/login" \
  -H "Origin: http://localhost:5173" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: Content-Type")
CODE=$(echo "$RESP" | tail -1)
assert_status "8.1 CORS preflight returns 200" "200" "$CODE"

# ═══════════════════════════════════════════════════════════════
#  SUMMARY
# ═══════════════════════════════════════════════════════════════
echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════${NC}"
echo -e "  Results: ${GREEN}$PASS passed${NC}, ${RED}$FAIL failed${NC}, $TOTAL total"
echo -e "${CYAN}═══════════════════════════════════════════════════${NC}"

if [ $FAIL -eq 0 ]; then
  echo -e "  ${GREEN}ALL TESTS PASSED!${NC}"
else
  echo -e "  ${RED}$FAIL test(s) failed — review output above${NC}"
fi
echo ""
