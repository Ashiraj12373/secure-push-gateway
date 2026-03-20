import { useState, useEffect } from "react";
import { useParams, useNavigate } from "react-router-dom";
import { scansApi } from "../services/api";
import Navbar from "../components/Navbar";

const SEVERITY_STYLES = {
  CRITICAL: {
    badge: "bg-red-100 text-red-800 border border-red-200",
    card: "bg-red-50 border-red-200 text-red-600",
  },
  HIGH: {
    badge: "bg-orange-100 text-orange-800 border border-orange-200",
    card: "bg-orange-50 border-orange-200 text-orange-600",
  },
  MEDIUM: {
    badge: "bg-yellow-100 text-yellow-800 border border-yellow-200",
    card: "bg-yellow-50 border-yellow-200 text-yellow-600",
  },
  LOW: {
    badge: "bg-blue-100 text-blue-800 border border-blue-200",
    card: "bg-blue-50 border-blue-200 text-blue-600",
  },
};

function SeverityBadge({ severity }) {
  const style = SEVERITY_STYLES[severity]?.badge ?? "bg-gray-100 text-gray-700";
  return (
    <span className={`text-xs font-bold px-2 py-0.5 rounded-full ${style}`}>
      {severity}
    </span>
  );
}

function StatusChip({ status }) {
  const map = {
    PASS: { cls: "bg-green-100 text-green-800", label: "✅ PASS" },
    FAIL: { cls: "bg-red-100 text-red-800", label: "❌ FAIL" },
    PENDING: { cls: "bg-yellow-100 text-yellow-800", label: "⏳ PENDING" },
  };
  const { cls, label } = map[status] ?? { cls: "bg-gray-100 text-gray-700", label: status };
  return (
    <span className={`text-sm font-semibold px-3 py-1.5 rounded-full ${cls}`}>{label}</span>
  );
}

export default function ScanDetail() {
  const { scanId } = useParams();
  const navigate = useNavigate();
  const [scan, setScan] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    scansApi
      .getById(scanId)
      .then((res) => setScan(res.data))
      .catch(() => setError("Scan not found or you don't have access."))
      .finally(() => setLoading(false));
  }, [scanId]);

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50">
        <Navbar />
        <div className="flex items-center justify-center h-64">
          <div className="text-center">
            <div className="text-4xl animate-pulse mb-3">🔍</div>
            <p className="text-gray-400">Loading scan results…</p>
          </div>
        </div>
      </div>
    );
  }

  if (error || !scan) {
    return (
      <div className="min-h-screen bg-gray-50">
        <Navbar />
        <div className="max-w-4xl mx-auto px-6 py-8">
          <div className="bg-red-50 border border-red-200 text-red-700 rounded-xl p-4">
            {error || "Scan not found"}
          </div>
          <button
            onClick={() => navigate(-1)}
            className="text-indigo-600 text-sm mt-4 hover:underline"
          >
            ← Back
          </button>
        </div>
      </div>
    );
  }

  const vulns = scan.vulnerabilities ?? [];
  const counts = { CRITICAL: 0, HIGH: 0, MEDIUM: 0, LOW: 0 };
  vulns.forEach((v) => { if (counts[v.severity] !== undefined) counts[v.severity]++; });

  return (
    <div className="min-h-screen bg-gray-50">
      <Navbar />
      <div className="max-w-6xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {/* Breadcrumb */}
        <button
          onClick={() => navigate(-1)}
          className="flex items-center gap-1 text-indigo-600 text-sm mb-6 hover:underline"
        >
          ← Back to Dashboard
        </button>

        {/* Scan header card */}
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-6 mb-6">
          <div className="flex flex-col sm:flex-row sm:items-start sm:justify-between gap-4">
            <div>
              <h2 className="text-xl font-bold text-gray-900">{scan.repoName}</h2>
              <div className="flex flex-wrap gap-4 mt-2 text-sm text-gray-500">
                <span>
                  Branch:{" "}
                  <strong className="text-gray-700">{scan.branch ?? "—"}</strong>
                </span>
                <span>
                  Commit:{" "}
                  <code className="bg-gray-100 text-gray-700 px-1.5 py-0.5 rounded text-xs font-mono">
                    {scan.commitSha?.substring(0, 7) ?? "—"}
                  </code>
                </span>
                {scan.developer && (
                  <span>
                    Developer:{" "}
                    <strong className="text-gray-700">{scan.developer.username}</strong>
                  </span>
                )}
                {scan.scannedAt && (
                  <span>{new Date(scan.scannedAt).toLocaleString()}</span>
                )}
              </div>
            </div>
            <StatusChip status={scan.status} />
          </div>
        </div>

        {/* Severity summary */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-6">
          {Object.entries(counts).map(([sev, count]) => {
            const { card } = SEVERITY_STYLES[sev];
            return (
              <div key={sev} className={`rounded-xl border p-4 ${card}`}>
                <p className="text-xs font-medium uppercase tracking-wide opacity-70">{sev}</p>
                <p className="text-3xl font-bold mt-1">{count}</p>
              </div>
            );
          })}
        </div>

        {/* Vulnerabilities list */}
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
          <div className="px-6 py-4 border-b border-gray-100 flex items-center justify-between">
            <h3 className="font-semibold text-gray-900">
              Vulnerabilities{" "}
              <span className="text-gray-400 font-normal">({vulns.length})</span>
            </h3>
          </div>

          {vulns.length === 0 ? (
            <div className="text-center py-16">
              <p className="text-5xl mb-4">🎉</p>
              <p className="text-lg font-semibold text-gray-700">No vulnerabilities found!</p>
              <p className="text-gray-400 text-sm mt-2">
                This push passed all {scan.status === "PASS" ? "25+" : ""} security checks.
              </p>
            </div>
          ) : (
            <div className="divide-y divide-gray-100">
              {vulns.map((v, i) => (
                <div key={v.id ?? i} className="p-5 hover:bg-gray-50 transition-colors">
                  <div className="flex items-start gap-4">
                    <div className="flex-shrink-0 mt-0.5">
                      <SeverityBadge severity={v.severity} />
                    </div>
                    <div className="flex-1 min-w-0">
                      <div className="flex flex-wrap items-center gap-2 mb-1.5">
                        <code className="text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded font-mono">
                          {v.ruleId}
                        </code>
                        <span className="text-sm text-gray-600 font-medium truncate">
                          {v.fileName}
                          {v.lineNumber > 0 && (
                            <span className="text-gray-400 font-normal">:{v.lineNumber}</span>
                          )}
                        </span>
                      </div>
                      <p className="text-sm text-gray-800 leading-relaxed">{v.description}</p>
                      {v.codeSnippet && (
                        <pre className="mt-3 bg-gray-900 text-green-300 text-xs p-4 rounded-xl overflow-x-auto font-mono whitespace-pre-wrap leading-relaxed">
                          {v.codeSnippet}
                        </pre>
                      )}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
