import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { AuthProvider, useAuth } from "./context/AuthContext";
import Login from "./pages/Login";
import OwnerDashboard from "./pages/OwnerDashboard";
import DeveloperDashboard from "./pages/DeveloperDashboard";
import ScanDetail from "./pages/ScanDetail";
import ManualScan from "./pages/ManualScan";
import ProtectedRoute from "./components/ProtectedRoute";

function AppRoutes() {
  const { user } = useAuth();
  return (
    <Routes>
      <Route
        path="/login"
        element={!user ? <Login /> : <Navigate to="/dashboard" replace />}
      />
      <Route
        path="/dashboard"
        element={
          <ProtectedRoute>
            {user?.role === "OWNER" ? <OwnerDashboard /> : <DeveloperDashboard />}
          </ProtectedRoute>
        }
      />
      <Route
        path="/scans/:scanId"
        element={
          <ProtectedRoute>
            <ScanDetail />
          </ProtectedRoute>
        }
      />
      <Route
        path="/scan"
        element={
          <ProtectedRoute>
            <ManualScan />
          </ProtectedRoute>
        }
      />
      <Route
        path="*"
        element={<Navigate to={user ? "/dashboard" : "/login"} replace />}
      />
    </Routes>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AppRoutes />
      </AuthProvider>
    </BrowserRouter>
  );
}
