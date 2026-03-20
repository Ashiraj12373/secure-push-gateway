import { useNavigate } from "react-router-dom";
import { useAuth } from "../context/AuthContext";
import NotificationBell from "./NotificationBell";

export default function Navbar() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate("/login");
  };

  return (
    <nav className="bg-white border-b border-gray-200 shadow-sm sticky top-0 z-40">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          {/* Brand */}
          <button
            onClick={() => navigate("/dashboard")}
            className="flex items-center gap-2 hover:opacity-80 transition-opacity"
          >
            <span className="text-2xl">🔒</span>
            <span className="font-bold text-gray-900 text-lg hidden sm:block">
              Secure Push Gateway
            </span>
          </button>

          {/* Right side */}
          <div className="flex items-center gap-3">
            <button
              onClick={() => navigate("/scan")}
              className="text-sm bg-green-600 hover:bg-green-500 text-white px-3 py-1.5 rounded-lg transition-colors font-medium"
            >
              Scan Code
            </button>
            {user && <NotificationBell userId={user.userId} />}
            <div className="flex items-center gap-2">
              <span className="text-sm font-medium text-gray-700 hidden sm:block">
                {user?.username}
              </span>
              <span
                className={`text-xs font-semibold px-2 py-0.5 rounded-full ${
                  user?.role === "OWNER"
                    ? "bg-purple-100 text-purple-700"
                    : "bg-indigo-100 text-indigo-700"
                }`}
              >
                {user?.role}
              </span>
            </div>
            <button
              onClick={handleLogout}
              className="text-sm bg-gray-100 hover:bg-gray-200 text-gray-600 px-3 py-1.5 rounded-lg transition-colors"
            >
              Sign out
            </button>
          </div>
        </div>
      </div>
    </nav>
  );
}
