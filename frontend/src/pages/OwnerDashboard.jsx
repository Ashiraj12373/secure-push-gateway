import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend,
  ResponsiveContainer, PieChart, Pie, Cell,
} from "recharts";
import { scansApi, developersApi } from "../services/api";
import Navbar from "../components/Navbar";

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

const PIE_COLORS = ["#22c55e", "#ef4444"];

export default function OwnerDashboard() {
  const [stats, setStats] = useState(null);
  const [scans, setScans] = useState([]);
  const [developers, setDevelopers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const navigate = useNavigate();

  useEffect(() => {
    Promise.all([scansApi.getStats(), scansApi.getAll(), developersApi.getAll()])
      .then(([s, sc, d]) => {
        setStats(s.data);
        setScans(sc.data);
        setDevelopers(d.data);
      })
      .catch((err) => setError(err.response?.data || "Failed to load dashboard"))
      .finally(() => setLoading(false));
  }, []);

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-50">
        <Navbar />
        <div className="flex items-center justify-center h-64">
          <div className="text-center">
            <div className="text-4xl animate-pulse mb-3">🔍</div>
            <p className="text-gray-400">Loading dashboard…</p>
          </div>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen bg-gray-50">
        <Navbar />
        <div className="max-w-7xl mx-auto px-6 py-8">
          <div className="bg-red-50 border border-red-200 text-red-700 rounded-xl p-4">{error}</div>
        </div>
      </div>
    );
  }

  // Pie chart data
  const pieData = [
    { name: "Pass", value: stats?.pass ?? 0 },
    { name: "Fail", value: stats?.fail ?? 0 },
  ].filter((d) => d.value > 0);

  // Bar chart: developer scan breakdown
  const barData = developers
    .map((dev) => {
      const devScans = scans.filter((s) => s.developer?.id === dev.id);
      return {
        name: dev.username,
        Pass: devScans.filter((s) => s.status === "PASS").length,
        Fail: devScans.filter((s) => s.status === "FAIL").length,
      };
    })
    .filter((d) => d.Pass + d.Fail > 0)
    .slice(0, 10);

  return (
    <div className="min-h-screen bg-gray-50">
      <Navbar />
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8">
        <h2 className="text-xl font-bold text-gray-900 mb-6">Owner Dashboard</h2>

        {/* Stat cards */}
        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
          <StatCard label="Total Scans" value={stats?.total ?? 0} icon="📊" />
          <StatCard
            label="Pass Rate"
            value={`${(stats?.passRate ?? 0).toFixed(1)}%`}
            color="text-green-600"
            icon="✅"
          />
          <StatCard
            label="Failed Scans"
            value={stats?.fail ?? 0}
            color="text-red-600"
            icon="❌"
          />
          <StatCard
            label="Developers"
            value={developers.length}
            color="text-indigo-600"
            icon="👨‍💻"
          />
        </div>

        {/* Charts row */}
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6 mb-8">
          {/* Pie chart */}
          <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-5">
            <h3 className="text-sm font-semibold text-gray-700 mb-4">Overall Pass / Fail</h3>
            {pieData.length > 0 ? (
              <ResponsiveContainer width="100%" height={200}>
                <PieChart>
                  <Pie
                    data={pieData}
                    dataKey="value"
                    nameKey="name"
                    outerRadius={75}
                    label={({ name, percent }) =>
                      `${name} ${(percent * 100).toFixed(0)}%`
                    }
                    labelLine={false}
                  >
                    {pieData.map((_, i) => (
                      <Cell key={i} fill={PIE_COLORS[i]} />
                    ))}
                  </Pie>
                  <Tooltip />
                </PieChart>
              </ResponsiveContainer>
            ) : (
              <div className="flex items-center justify-center h-48 text-gray-400 text-sm">
                No scan data yet
              </div>
            )}
          </div>

          {/* Bar chart */}
          <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-5 lg:col-span-2">
            <h3 className="text-sm font-semibold text-gray-700 mb-4">Scans by Developer</h3>
            {barData.length > 0 ? (
              <ResponsiveContainer width="100%" height={200}>
                <BarChart data={barData} margin={{ top: 0, right: 0, left: -20, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" />
                  <XAxis dataKey="name" tick={{ fontSize: 11 }} />
                  <YAxis tick={{ fontSize: 11 }} allowDecimals={false} />
                  <Tooltip />
                  <Legend wrapperStyle={{ fontSize: 12 }} />
                  <Bar dataKey="Pass" fill="#22c55e" radius={[3, 3, 0, 0]} />
                  <Bar dataKey="Fail" fill="#ef4444" radius={[3, 3, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            ) : (
              <div className="flex items-center justify-center h-48 text-gray-400 text-sm">
                No scan data yet
              </div>
            )}
          </div>
        </div>

        {/* Tables row */}
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
          {/* Recent scans */}
          <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
            <div className="px-5 py-4 border-b border-gray-100">
              <h3 className="font-semibold text-gray-900 text-sm">Recent Scans</h3>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-gray-50 border-b border-gray-100">
                    <th className="text-left px-4 py-3 text-xs font-medium text-gray-500">Developer</th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-gray-500">Repository</th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-gray-500">Status</th>
                    <th className="text-right px-4 py-3 text-xs font-medium text-gray-500">Vulns</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-50">
                  {scans.slice(0, 10).map((scan) => (
                    <tr
                      key={scan.id}
                      onClick={() => navigate(`/scans/${scan.id}`)}
                      className="hover:bg-indigo-50 cursor-pointer transition-colors"
                    >
                      <td className="px-4 py-3 font-medium text-gray-800">
                        {scan.developer?.username ?? "—"}
                      </td>
                      <td
                        className="px-4 py-3 text-gray-500 max-w-[130px] truncate"
                        title={scan.repoName}
                      >
                        {scan.repoName?.split("/").pop() ?? scan.repoName}
                      </td>
                      <td className="px-4 py-3">
                        <StatusBadge status={scan.status} />
                      </td>
                      <td className="px-4 py-3 text-right text-gray-600">
                        {scan.totalVulnerabilities}
                      </td>
                    </tr>
                  ))}
                  {scans.length === 0 && (
                    <tr>
                      <td colSpan="4" className="text-center text-gray-400 py-10 text-sm">
                        No scans yet. Connect a GitHub webhook to get started.
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>

          {/* Developers */}
          <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden">
            <div className="px-5 py-4 border-b border-gray-100">
              <h3 className="font-semibold text-gray-900 text-sm">Developers</h3>
            </div>
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="bg-gray-50 border-b border-gray-100">
                    <th className="text-left px-4 py-3 text-xs font-medium text-gray-500">Username</th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-gray-500">Email</th>
                    <th className="text-left px-4 py-3 text-xs font-medium text-gray-500">Streak</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-gray-50">
                  {developers.map((dev) => (
                    <tr key={dev.id} className="hover:bg-gray-50 transition-colors">
                      <td className="px-4 py-3 font-medium text-gray-800">{dev.username}</td>
                      <td className="px-4 py-3 text-gray-500 text-xs">{dev.email}</td>
                      <td className="px-4 py-3">
                        {dev.streak > 0 ? (
                          <span className="text-orange-500 font-semibold">🔥 {dev.streak}</span>
                        ) : (
                          <span className="text-gray-300">—</span>
                        )}
                      </td>
                    </tr>
                  ))}
                  {developers.length === 0 && (
                    <tr>
                      <td colSpan="3" className="text-center text-gray-400 py-10 text-sm">
                        No developers registered yet
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
