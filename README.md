# Secure Push Gateway

A full-stack **automated code security scanning platform** that integrates with GitHub webhooks to detect vulnerabilities in every push. Built with **Spring Boot 3** (backend) and **React + Vite** (frontend).

When a developer pushes code to a connected GitHub repo, the system automatically scans modified Java and JavaScript files for **25+ vulnerability rules** — including hardcoded secrets, SQL injection, XSS, command injection, weak cryptography, and more. Results are persisted, visualized on role-based dashboards, and sent via email notifications.

---

## Architecture

```
Developer → git push → GitHub
                          │
                          ▼
               POST /api/webhook/github   ← HMAC-SHA256 validated
                          │
                          ▼
               ScanOrchestrationService   ← @Async (returns 200 immediately)
                          │
                    ┌─────┴──────────────┐
                    ▼                    ▼
              Java files              JS/TS files
              (JavaParser AST)        (Regex engine)
              7 visitor rules         12 pattern rules
                    │                    │
                    └──────┬─────────────┘
                           ▼
                     MySQL — scan + vulnerability results persisted
                           │
                    ┌──────┴──────────────────┐
                    ▼                         ▼
              Email (Thymeleaf)         In-app notifications
              Developer + Owner alert   Polled every 30s by frontend
                           │
                    Badge evaluation (streak / weekly logic)
                           │
                    React Dashboard auto-updates
```

**Manual Scan Flow:**
```
User → Paste Code → POST /api/scans/manual → ScanEngineService → Results displayed in browser
```

---

## Features

- **GitHub Webhook Integration** — Automatic scanning on every `git push`
- **Static Analysis Engine** — JavaParser AST analysis (Java) + regex-based scanning (JavaScript/TypeScript)
- **25+ Security Rules** — Hardcoded secrets, SQL injection, XSS, command injection, weak crypto, unsafe deserialization, prototype pollution, and more
- **Manual Code Scan** — Paste code directly in the browser to test the scanner without a GitHub repo
- **JWT Authentication** — Stateless, HMAC-signed token-based auth with BCrypt password hashing
- **Role-Based Dashboards** — Owner (admin overview, charts, all developers) vs Developer (personal scans, badges)
- **Badge Gamification** — Earn badges for clean push streaks (First Clean Push, Streak 3/5/10, Clean Week)
- **Email Notifications** — Scan results and badge awards via Gmail SMTP with HTML templates
- **In-App Notifications** — Real-time notification bell with 30-second polling
- **HMAC Webhook Validation** — Constant-time signature verification for GitHub webhooks

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Java 21, Spring Boot 3.3.0, Spring Security, Spring Data JPA |
| Frontend | React 18, Vite 5, Tailwind CSS 3, Recharts (charts) |
| Database | MySQL 8.0+ |
| Auth | JWT (JJWT 0.12.5), BCrypt password hashing |
| Static Analysis | JavaParser 3.25.8 (AST), Regex patterns |
| Email | Spring Mail + Thymeleaf HTML templates |
| HTTP Client | Axios (frontend), RestTemplate (backend GitHub API calls) |
| Build | Maven (backend), npm/Vite (frontend) |

---

## Prerequisites

Before setting up, make sure you have these installed:

| Tool | Version | Check Command |
|------|---------|--------------|
| **Java JDK** | 21+ | `java -version` |
| **Maven** | 3.9+ | `mvn -version` |
| **Node.js** | 18+ (LTS recommended: v22) | `node -v` |
| **npm** | 9+ | `npm -v` |
| **MySQL** | 8.0+ | `mysql --version` |
| **Git** | 2.x | `git --version` |

---

## Setup Instructions

### 1. Clone the Repository

```bash
git clone https://github.com/YOUR_USERNAME/secure-push-gateway.git
cd secure-push-gateway
```

### 2. Set Up MySQL Database

```sql
-- Connect to MySQL
mysql -u root -p

-- Create the database (or let Hibernate auto-create it)
CREATE DATABASE IF NOT EXISTS securepushgateway;
```

> **Note:** The app uses `spring.jpa.hibernate.ddl-auto=update`, so all tables (users, scans, vulnerability_results, badges, notifications) are created automatically on first run.

### 3. Configure Environment Variables

You can set environment variables or edit `backend/src/main/resources/application.properties` directly.

#### Option A: Environment Variables

**Linux / macOS:**
```bash
export DB_URL="jdbc:mysql://localhost:3306/securepushgateway?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC"
export DB_USERNAME="root"
export DB_PASSWORD="your_mysql_password"
export JWT_SECRET="your-256-bit-secret-key-change-this-in-production"
```

**Windows (PowerShell):**
```powershell
$env:DB_URL = "jdbc:mysql://localhost:3306/securepushgateway?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC"
$env:DB_USERNAME = "root"
$env:DB_PASSWORD = "your_mysql_password"
$env:JWT_SECRET = "your-256-bit-secret-key-change-this-in-production"
```

**Windows (CMD):**
```cmd
set DB_URL=jdbc:mysql://localhost:3306/securepushgateway?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC
set DB_USERNAME=root
set DB_PASSWORD=your_mysql_password
set JWT_SECRET=your-256-bit-secret-key-change-this-in-production
```

#### Option B: Edit application.properties directly

Open `backend/src/main/resources/application.properties` and fill in your values:
```properties
spring.datasource.url=jdbc:mysql://localhost:3306/securepushgateway?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC
spring.datasource.username=root
spring.datasource.password=YOUR_PASSWORD_HERE
jwt.secret=YOUR_SECRET_HERE
```

#### Optional Configuration

```bash
# GitHub Integration (needed for webhook scanning)
export GITHUB_TOKEN="ghp_your_personal_access_token"
export GITHUB_WEBHOOK_SECRET="your-webhook-secret"

# Email Notifications via Gmail
export MAIL_USERNAME="your-email@gmail.com"
export MAIL_PASSWORD="your-gmail-app-password"
```

> **Note:** If `GITHUB_WEBHOOK_SECRET` is left empty, webhook signature validation is skipped (dev mode). Email sending is optional — the app works without it.

### 4. Start the Backend

```bash
cd backend
mvn spring-boot:run
```

The backend starts on **http://localhost:8080**.

On first startup, a default admin account is seeded automatically:
- **Username:** `admin`
- **Password:** `admin123`
- **Role:** OWNER

### 5. Start the Frontend

```bash
cd frontend
npm install
npm run dev
```

The frontend starts on **http://localhost:5173**.

### 6. Open the Application

1. Open **http://localhost:5173** in your browser
2. Log in with `admin` / `admin123` (Owner account)
3. Or click **Register** to create a new Developer account
4. Click **Scan Code** in the navbar to test the manual scanner
5. Use the **Load Java Sample** or **Load JS Sample** buttons to load pre-built vulnerable code

---

## Usage Guide

### Manual Code Scanning (No GitHub required)

1. Log in to the application
2. Click **"Scan Code"** in the navigation bar
3. Either:
   - Click **"Load Java Sample (with vulns)"** to load sample vulnerable Java code
   - Click **"Load JS Sample (with vulns)"** to load sample vulnerable JavaScript code
   - Or paste your own Java/JavaScript code
4. Click **"Run Scan"**
5. View results: severity badges (CRITICAL/HIGH/MEDIUM/LOW), rule IDs, line numbers, code snippets, and remediation advice

### GitHub Webhook Integration (Automatic scanning on push)

#### Step 1: Generate a GitHub Personal Access Token
1. Go to **GitHub → Settings → Developer settings → Personal access tokens → Fine-grained tokens**
2. Click **"Generate new token"**
3. Give it **Contents: Read-only** permission for your target repo
4. Copy the token (starts with `ghp_...` or `github_pat_...`)

#### Step 2: Set Environment Variables
```bash
export GITHUB_TOKEN="ghp_your_token_here"
export GITHUB_WEBHOOK_SECRET="my-webhook-secret-123"
```

#### Step 3: Expose Your Local Server
GitHub can't reach `localhost`, so use [ngrok](https://ngrok.com):
```bash
ngrok http 8080
# This gives you a URL like https://abc123.ngrok-free.app
```

#### Step 4: Add Webhook to Your GitHub Repo
1. Go to your **GitHub repo → Settings → Webhooks → Add webhook**
2. Fill in:
   - **Payload URL:** `https://YOUR_NGROK_URL/api/webhook/github`
   - **Content type:** `application/json`
   - **Secret:** `my-webhook-secret-123` (must match your env variable)
   - **Events:** Just the **push** event
3. Click **Add webhook**

#### Step 5: Push Code and Watch It Scan
```bash
# Create a file with vulnerabilities
echo 'public class Test { String password = "secret123"; }' > Test.java
git add Test.java
git commit -m "test vulnerability scanning"
git push
```

The webhook fires, the scan runs asynchronously, and results appear on the dashboard.

---

## API Endpoints

### Authentication
| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| POST | `/api/auth/login` | Login, returns JWT token | No |
| POST | `/api/auth/register` | Register new user | No |
| GET | `/api/auth/me` | Get current authenticated user | Yes |

### Scans
| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| GET | `/api/scans` | List all scans (Owner) or own scans (Developer) | Yes |
| GET | `/api/scans/{id}` | Get scan detail with vulnerability list | Yes |
| GET | `/api/scans/developer/{id}` | Get a specific developer's scans | Yes |
| GET | `/api/scans/stats` | Dashboard statistics | Yes (Owner) |
| POST | `/api/scans/manual` | Submit code for manual scanning | Yes |

### Developers
| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| GET | `/api/developers` | List all developers | Yes (Owner) |
| GET | `/api/developers/{id}` | Get developer profile with stats | Yes |

### Badges
| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| GET | `/api/badges/{developerId}` | Get developer's earned badges | Yes |
| POST | `/api/badges/evaluate/{developerId}` | Trigger badge evaluation | Yes |

### Notifications
| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| GET | `/api/notifications/{userId}` | Get user's notifications | Yes |
| PUT | `/api/notifications/{id}/read` | Mark notification as read | Yes |

### Webhook
| Method | Endpoint | Description | Auth Required |
|--------|----------|-------------|---------------|
| POST | `/api/webhook/github` | GitHub push event receiver | No (HMAC validated) |

---

## Security Rules

### Java Rules (AST-based via JavaParser)

| Rule ID | Severity | What It Detects |
|---------|----------|-----------------|
| JAVA-001 | CRITICAL | Hardcoded secrets (passwords, API keys, tokens in String variables) |
| JAVA-002 | CRITICAL | SQL injection via string concatenation in JDBC queries |
| JAVA-003 | HIGH | Unsafe deserialization via `ObjectInputStream.readObject()` |
| JAVA-004a | CRITICAL | OS command injection via `Runtime.exec()` |
| JAVA-004b | MEDIUM | `System.exit()` — abrupt JVM termination bypasses security handlers |
| JAVA-004c | HIGH | `ProcessBuilder` with string concatenation |
| JAVA-005 | HIGH | XSS — unescaped variable written to HTTP response writer |
| JAVA-006 | HIGH | Weak cryptography (MD5, SHA-1, DES, RC4, RC2) |
| JAVA-007 | MEDIUM | Potential NullPointerException on chained method calls |

### JavaScript Rules (Regex-based)

| Rule ID | Severity | What It Detects |
|---------|----------|-----------------|
| JS-001 | CRITICAL | Hardcoded secrets and credentials in source code |
| JS-002 | HIGH | `eval()` usage — arbitrary code execution |
| JS-003 | HIGH | `innerHTML` XSS — direct assignment with non-literal value |
| JS-004 | HIGH | `document.write()` with dynamic content |
| JS-005 | CRITICAL | SQL injection via template literal interpolation |
| JS-006 | MEDIUM | Sensitive data logged to `console.log` |
| JS-007 | HIGH | Prototype pollution via `__proto__` or unsafe `Object.assign` |
| JS-008 | LOW | `Math.random()` used where crypto-secure random needed |
| JS-009 | MEDIUM | React `dangerouslySetInnerHTML` without sanitization |
| JS-010 | MEDIUM | Open redirect via `location.href` assignment |
| JS-011 | LOW | Hardcoded localhost/IP URLs |
| JS-012 | MEDIUM | `setTimeout`/`setInterval` with string argument (eval equivalent) |

---

## Badge System

| Badge | Requirement | Description |
|-------|-------------|-------------|
| First Clean Push | 1 clean scan | First ever vulnerability-free commit |
| Streak 3 | 3 consecutive clean scans | Three clean pushes in a row |
| Streak 5 | 5 consecutive clean scans | Five clean pushes in a row |
| Streak 10 | 10 consecutive clean scans | Ten clean pushes — security champion! |
| Clean Week | All scans in a calendar week pass | Entire week of vulnerability-free pushes |

---

## Project Structure

```
secure-push-gateway/
├── backend/
│   ├── pom.xml                                  # Maven build (Spring Boot 3.3.0, Java 21)
│   └── src/main/java/com/securepushgateway/
│       ├── SecurePushGatewayApplication.java     # Entry point (@EnableAsync)
│       ├── config/
│       │   ├── SecurityConfig.java               # JWT filter, CORS, Spring Security rules
│       │   ├── AppConfig.java                    # Beans: UserDetailsService, AuthManager, RestTemplate
│       │   └── DataSeeder.java                   # Seeds default admin account on startup
│       ├── controller/
│       │   ├── AuthController.java               # POST /api/auth/login, /register, GET /me
│       │   ├── ScanController.java               # GET/POST /api/scans/*, manual scan
│       │   ├── DeveloperController.java          # GET /api/developers/*
│       │   ├── BadgeController.java              # GET/POST /api/badges/*
│       │   ├── NotificationController.java       # GET/PUT /api/notifications/*
│       │   └── WebhookController.java            # POST /api/webhook/github
│       ├── engine/
│       │   └── ScanEngineService.java            # Core scan engine (7 AST visitors + 12 regex rules)
│       ├── model/
│       │   ├── User.java                         # User entity (OWNER/DEVELOPER roles)
│       │   ├── Scan.java                         # Scan entity (PASS/FAIL/PENDING)
│       │   ├── VulnerabilityResult.java          # Vulnerability finding (CRITICAL/HIGH/MEDIUM/LOW)
│       │   ├── Badge.java                        # Gamification badge entity
│       │   └── Notification.java                 # In-app notification entity
│       ├── repository/                           # Spring Data JPA repositories (5 interfaces)
│       ├── service/
│       │   ├── ScanOrchestrationService.java     # @Async webhook → GitHub API → scan → persist
│       │   ├── BadgeService.java                 # Streak calculation + badge award logic
│       │   ├── NotificationService.java          # In-app notification creation
│       │   └── EmailService.java                 # Thymeleaf email sending (scan results + badges)
│       └── util/
│           ├── JwtUtil.java                      # JWT generation, validation, extraction
│           └── HmacValidator.java                # GitHub HMAC-SHA256 signature validation
│
├── frontend/
│   ├── package.json                              # React 18, Vite 5, Tailwind 3, Recharts, Axios
│   ├── vite.config.js                            # Dev server config (port 5173)
│   ├── tailwind.config.js                        # Tailwind CSS config
│   └── src/
│       ├── App.jsx                               # React Router with role-based routing
│       ├── main.jsx                              # React entry point
│       ├── services/api.js                       # Axios HTTP client + JWT interceptor
│       ├── context/AuthContext.jsx                # Auth state management (login/logout)
│       ├── hooks/useNotifications.js              # 30-second notification polling hook
│       ├── pages/
│       │   ├── Login.jsx                         # Login + Register with tab switching
│       │   ├── OwnerDashboard.jsx                # Admin view: stats, charts, developer list
│       │   ├── DeveloperDashboard.jsx            # Developer view: scans, badges, streaks
│       │   ├── ScanDetail.jsx                    # Vulnerability detail view for a scan
│       │   └── ManualScan.jsx                    # Manual code submission + results display
│       └── components/
│           ├── Navbar.jsx                        # Top nav with Scan Code button + notifications
│           ├── NotificationBell.jsx              # Notification dropdown with unread count
│           └── ProtectedRoute.jsx                # Auth guard (redirects to /login)
│
├── test-e2e.sh                                   # 44 API endpoint E2E tests
├── test-manual-scan.sh                           # Manual scan test suite (Java + JS + clean code)
└── README.md
```

---

## Running Tests

### API End-to-End Tests
```bash
# Make sure the backend is running on localhost:8080
bash test-e2e.sh
```
Tests 44 scenarios across: authentication, webhook handling, scan CRUD, RBAC, notifications, badges, security headers, and CORS.

### Manual Scan Tests
```bash
bash test-manual-scan.sh
```
Tests vulnerability detection for Java (hardcoded secrets, SQL injection, Runtime.exec, weak crypto), JavaScript (eval, innerHTML, template literal SQL, console.log secrets, setTimeout), clean code (should PASS), unsupported file types, and auth requirements.

### Unit Tests
```bash
cd backend
mvn test
```
Runs `ScanEngineServiceTest` with test cases for all Java and JavaScript rules.

---

## Security Notes

- **JWT stored in memory only** — not in localStorage or sessionStorage (immune to XSS token theft)
- **Webhook HMAC validation** uses constant-time comparison to prevent timing attacks
- **CSRF disabled** — stateless JWT auth doesn't need CSRF tokens
- **Passwords hashed with BCrypt** via Spring Security's `PasswordEncoder`
- **All secrets loaded from environment variables** — never hardcoded in source
- **Role-based access control** — Owner vs Developer permissions enforced at controller level

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| MySQL `Connection refused` | Ensure MySQL is running: `sudo systemctl start mysql` (Linux) or `net start MySQL80` (Windows) |
| `Access denied` for MySQL | Verify `DB_USERNAME` and `DB_PASSWORD` match your MySQL credentials |
| Frontend shows "Network error" | Backend must be running on port 8080 — check `mvn spring-boot:run` output |
| Login fails with `admin/admin123` | Check backend startup logs for "Default OWNER account created" message |
| Webhook returns 401 | Ensure `GITHUB_WEBHOOK_SECRET` matches in both app config and GitHub webhook settings |
| Scans always PASS on webhook | Set `GITHUB_TOKEN` — without it, the app can't fetch file contents from GitHub API |
| Email not sending | Use a Gmail **App Password** (not your real password) — enable 2FA on Gmail first |
| `mvn: command not found` | Install Maven: `brew install maven` (macOS), `sudo apt install maven` (Ubuntu), or [download](https://maven.apache.org/download.cgi) |
| `npm: command not found` | Install Node.js LTS from [nodejs.org](https://nodejs.org) |

---

## License

This project is for educational and portfolio purposes.

---

## Author

Built by **Ashish** — Full-stack security scanning platform demonstrating Spring Boot, React, static analysis, and DevSecOps integration.
