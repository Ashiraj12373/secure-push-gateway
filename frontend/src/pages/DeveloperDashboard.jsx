import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import { scansApi, badgesApi } from "../services/api";
import Navbar from "../components/Navbar";

const BADGE_META = {
  FIRST_CLEAN_PUSH: { icon: "🌟", label: "First Clean Push" },
  STREAK_3: { icon: "🔥", label: "On Fire ×3" },
  STREAK_5: { icon: "💪", label: "Clean Streak ×5" },
  STREAK_10: { icon: "🏆", label: "Untouchable ×10" },
  CLEAN_WEEK: { icon: "✨", label: "Spotless Week" },
};

function StatusBadge({ status }) {
  const map = {
    PASS: "bg-green-100 text-green-800",
    FAIL: "bg-red-100 text-red-800",
    PENDING: "bg-yellow-100 text-yellow-800",
  };
  return (
    <span className={`text-xs font-semibold px-2 py-0.5 rounded-full ${map[status] ?? "bg-gray-100 text-gray-600"}`}>
      {status}
    </span>
  );
}

function StatCard({ label, value, color = "text-gray-900", icon }) {
  return (
    <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-5">
      <div className="flex items-start justify-between">
        <div>
          <p className="text-xs font-medium text-gray-500 uppercase tracking-wide">{label}</p>
          <p className={`text-3xl font-bold mt-1 ${color}`}>{value}</p>
        </div>
        {icon && <span className="text-2xl opacity-60">{icon}</span>}
      </div>
    </div>
  );
}

export default function DeveloperDashboard() {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [scans, setScans] = useState([]);
  const [badges, setBadges] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    Promise.all([scansApi.getAll(), badgesApi.getByDeveloper(user.userId)])
      .then(([sc, b]) => {
        setScans(sc.data);
        setBadges(b.data);
      })
      .catch((err) => setError(err.response?.data || "Failed to load data"))
      .finally(() => setLoading(false));
  }, [user.userId]);

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50">
        <Navbar />
        <div className="flex items-center justify-center h-64">
          <div className="text-center">
            <div className="text-4xl animate-pulse mb-3">🔍</div>
            <p className="text-gray-400">Loading your dashboard…</p>
          </div>
        </div>
      </div>
    );
  }

  const totalScans = scans.length;
  const passCount = scans.filter((s) => s.status === "PASS").length;
  const passRate = totalScans > 0 ? ((passCount / totalScans) * 100).toFixed(1) : "0.0";

  // Compute streak (scans come ordered desc by date)
  let streak = 0;
  for (const scan of scans) {
    if (scan.status === "PASS") streak++;
    else break;
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <Navbar />
      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <h2 className="text-xl font-bold text-gray-900 mb-6">
          Welcome back,{" "}
          <span className="text-indigo-600">{user.username}</span> 👋
        </h2>

        {error && (
          <div className="bg-red-50 border border-red-200 text-red-700 rounded-xl p-4 mb-6">
            {error}
          </div>
        )}

        {/* Stats */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
          <StatCard label="Total Scans" value={totalScans} icon="📊" />
          <StatCard label="Pass Rate" value={`${passRate}%`} color="text-green-600" icon="✅" />
          <StatCard
            label="Clean Streak"
            value={streak > 0 ? `🔥 ${streak}` : "—"}
            color="text-orange-500"
          />
          <StatCard
            label="Badges Earned"
            value={badges.length}
            color="text-purple-600"
            icon="🏅"
          />
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Scans table */}
          <div className="lg:col-span-2 bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
            <div className="px-5 py-4 border-b border-gray-100">
              <h3 className="font-semibold text-gray-900 text-sm">Recent Scans</h3>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-gray-50 border-b border-gray-100">
                    <th className="text-left px-4 py-3 text-xs font-medium text-gray-500">Repository</th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-gray-500">Branch</th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-gray-500">Status</th>
                    <th className="text-right px-4 py-3 text-xs font-medium text-gray-500">Vulns</th>
                    <th className="text-right px-4 py-3 text-xs font-medium text-gray-500">Date</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-50">
                  {scans.slice(0, 15).map((scan) => (
                    <tr
                      key={scan.id}
                      onClick={() => navigate(`/scans/${scan.id}`)}
                      className="hover:bg-indigo-50 cursor-pointer transition-colors"
                    >
                      <td
                        className="px-4 py-3 font-medium text-gray-800 max-w-[140px] truncate"
                        title={scan.repoName}
                      >
                        {scan.repoName?.split("/").pop() ?? scan.repoName}
                      </td>
                      <td className="px-4 py-3 text-gray-500 text-xs">{scan.branch ?? "—"}</td>
                      <td className="px-4 py-3">
                        <StatusBadge status={scan.status} />
                      </td>
                      <td className="px-4 py-3 text-right text-gray-600">
                        {scan.totalVulnerabilities}
                      </td>
                      <td className="px-4 py-3 text-right text-gray-400 text-xs">
                        {scan.scannedAt
                          ? new Date(scan.scannedAt).toLocaleDateString()
                          : "—"}
                      </td>
                    </tr>
                  ))}
                  {scans.length === 0 && (
                    <tr>
                      <td colSpan="5" className="text-center py-12">
                        <p className="text-3xl mb-2">🎉</p>
                        <p className="text-gray-500 font-medium">No scans yet</p>
                        <p className="text-gray-400 text-xs mt-1">
                          Push code to a connected repo to get started
                        </p>
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>

          {/* Badges */}
          <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
            <div className="px-5 py-4 border-b border-gray-100">
              <h3 className="font-semibold text-gray-900 text-sm">
                Badges{" "}
                {badges.length > 0 && (
                  <span className="text-indigo-600">({badges.length})</span>
                )}
              </h3>
            </div>
            <div className="p-4">
              {badges.length === 0 ? (
                <div className="text-center py-8">
                  <p className="text-4xl mb-3">🏅</p>
                  <p className="text-sm font-medium text-gray-600">No badges yet</p>
                  <p className="text-xs text-gray-400 mt-1">
                    Push clean code to start earning!
                  </p>
                </div>
              ) : (
                <div className="space-y-3">
                  {badges.map((badge) => {
                    const meta = BADGE_META[badge.badgeType] ?? {
                      icon: "🏆",
                      label: badge.badgeType?.replace(/_/g, " "),
                    };
                    return (
                      <div
                        key={badge.id}
                        className="flex items-center gap-3 p-3 bg-gradient-to-r from-indigo-50 to-purple-50 rounded-xl border border-indigo-100"
                      >
                        <span className="text-2xl flex-shrink-0">{meta.icon}</span>
                        <div className="min-w-0">
                          <p className="font-semibold text-sm text-gray-800">{meta.label}</p>
                          {badge.awardedAt && (
                            <p className="text-xs text-gray-400">
                              {new Date(badge.awardedAt).toLocaleDateString()}
                            </p>
                          )}
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
