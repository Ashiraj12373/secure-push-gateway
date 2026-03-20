import { useState } from "react";
import { scansApi } from "../services/api";
import Navbar from "../components/Navbar";

const SAMPLE_JAVA = `public class UserService {
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
}`;

const SAMPLE_JS = `const apiKey = "sk-live-abcdef123456";
const token = "ghp_xxxxxxxxxxxxxxxxxxxx";

function search(userInput) {
  const sql = \`SELECT * FROM items WHERE name = \${userInput}\`;
  db.query(sql);
  eval(userInput);
  document.getElementById("out").innerHTML = userInput;
  console.log("Debug token:", token);
  setTimeout("alert('hi')", 1000);
}`;

export default function ManualScan() {
  const [fileName, setFileName] = useState("Example.java");
  const [code, setCode] = useState("");
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  const handleScan = async () => {
    if (!code.trim()) { setError("Paste some code first"); return; }
    setError("");
    setLoading(true);
    setResult(null);
    try {
      const { data } = await scansApi.manual({ fileName, code, repoName: "manual-test" });
      setResult(data);
    } catch (err) {
      const msg = err.response?.data?.message
        || err.response?.data?.error
        || (err.response ? `Server error ${err.response.status}` : "Network error — is the backend running?");
      setError(msg);
      console.error("Scan error:", err.response?.data || err.message);
    } finally {
      setLoading(false);
    }
  };

  const loadSample = (type) => {
    if (type === "java") { setFileName("VulnerableService.java"); setCode(SAMPLE_JAVA); }
    else { setFileName("vulnerable-app.js"); setCode(SAMPLE_JS); }
    setResult(null);
  };

  const severityColor = {
    CRITICAL: "bg-red-100 text-red-800",
    HIGH: "bg-orange-100 text-orange-800",
    MEDIUM: "bg-yellow-100 text-yellow-800",
    LOW: "bg-blue-100 text-blue-800",
  };

  return (
    <div className="min-h-screen bg-gray-50">
      <Navbar />
      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-8 space-y-6">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">Manual Code Scan</h1>
          <p className="text-gray-500 mt-1">Paste code below to test the vulnerability scanner. Supports Java and JavaScript.</p>
        </div>

        <div className="flex gap-3">
          <button onClick={() => loadSample("java")}
            className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-sm font-medium transition-colors">
            Load Java Sample (with vulns)
          </button>
          <button onClick={() => loadSample("js")}
            className="px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg text-sm font-medium transition-colors">
            Load JS Sample (with vulns)
          </button>
        </div>

        <div className="bg-white rounded-xl shadow-sm border border-gray-200 p-6 space-y-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">File Name</label>
            <input value={fileName} onChange={e => setFileName(e.target.value)}
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-gray-900 text-sm focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
              placeholder="e.g. MyService.java or app.js" />
          </div>
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">Code</label>
            <textarea value={code} onChange={e => setCode(e.target.value)}
              rows={16}
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-gray-900 font-mono text-sm focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
              placeholder="Paste your Java or JavaScript code here..." />
          </div>

          {error && <p className="text-red-600 text-sm">{error}</p>}

          <button onClick={handleScan} disabled={loading}
            className="px-6 py-2.5 bg-green-600 hover:bg-green-700 disabled:opacity-50 text-white rounded-lg font-medium transition-colors">
            {loading ? "Scanning..." : "Run Scan"}
          </button>
        </div>

        {result && (
          <div className="space-y-4">
            <div className={`p-4 rounded-xl border ${result.status === "PASS" ? "bg-green-50 border-green-200" : "bg-red-50 border-red-200"}`}>
              <h2 className={`text-lg font-bold ${result.status === "PASS" ? "text-green-800" : "text-red-800"}`}>
                {result.status === "PASS" ? "PASS — No vulnerabilities found" : `FAIL — ${result.totalVulnerabilities} vulnerabilit${result.totalVulnerabilities === 1 ? "y" : "ies"} found`}
              </h2>
              <p className="text-sm text-gray-500 mt-1">Scan ID: {result.scanId}</p>
              {result.newBadges?.length > 0 && (
                <p className="text-amber-700 mt-2 font-medium">Badges earned: {result.newBadges.join(", ")}</p>
              )}
            </div>

            {result.vulnerabilities?.length > 0 && (
              <div className="space-y-3">
                {result.vulnerabilities.map((v, i) => (
                  <div key={i} className="bg-white border border-gray-200 rounded-xl shadow-sm p-4">
                    <div className="flex items-center gap-3 mb-2">
                      <span className={`px-2.5 py-0.5 text-xs font-bold rounded-full ${severityColor[v.severity]}`}>
                        {v.severity}
                      </span>
                      <span className="text-gray-500 text-sm font-mono">{v.ruleId}</span>
                      <span className="text-gray-400 text-sm">Line {v.lineNumber}</span>
                    </div>
                    <p className="text-gray-700 text-sm mb-2">{v.description}</p>
                    {v.codeSnippet && (
                      <pre className="bg-gray-50 border border-gray-200 rounded-lg p-3 text-xs text-gray-600 overflow-x-auto">{v.codeSnippet}</pre>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
