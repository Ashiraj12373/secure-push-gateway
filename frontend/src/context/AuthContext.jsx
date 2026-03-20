// src/context/AuthContext.jsx
import { createContext, useContext, useState, useCallback } from "react";
import { authApi, setToken, clearToken } from "../services/api";

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  // User stored in state only — token is in memory via api.js module scope
  const [user, setUser] = useState(null);

  const login = useCallback(async (username, password) => {
    const { data } = await authApi.login({ username, password });
    setToken(data.token);
    setUser({ username, role: data.role, userId: Number(data.userId) });
    return data;
  }, []);

  const logout = useCallback(() => {
    clearToken();
    setUser(null);
  }, []);

  return (
    <AuthContext.Provider value={{ user, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}
