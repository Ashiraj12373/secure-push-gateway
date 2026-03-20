// src/services/api.js
// ─── Axios client with JWT injection ────────────────────────────────────────

import axios from "axios";

const BASE_URL = import.meta.env.VITE_API_URL || "http://localhost:8080";

// Token stored in memory only (not localStorage) per spec
let jwtToken = null;
export const setToken = (t) => { jwtToken = t; };
export const clearToken = () => { jwtToken = null; };

const api = axios.create({ baseURL: BASE_URL });

api.interceptors.request.use((config) => {
  if (jwtToken) config.headers.Authorization = `Bearer ${jwtToken}`;
  return config;
});

api.interceptors.response.use(
  res => res,
  err => {
    if (err.response?.status === 401) {
      clearToken();
      window.location.href = "/login";
    }
    return Promise.reject(err);
  }
);

// ── Auth ──────────────────────────────────────────────────────────────────
export const authApi = {
  login:    (body)  => api.post("/api/auth/login", body),
  register: (body)  => api.post("/api/auth/register", body),
};

// ── Scans ──────────────────────────────────────────────────────────────────
export const scansApi = {
  getAll:         ()          => api.get("/api/scans"),
  getById:        (id)        => api.get(`/api/scans/${id}`),
  getByDeveloper: (devId)     => api.get(`/api/scans/developer/${devId}`),
  getStats:       ()          => api.get("/api/scans/stats"),
  manual:         (body)      => api.post("/api/scans/manual", body),
};

// ── Developers ─────────────────────────────────────────────────────────────
export const developersApi = {
  getAll: ()    => api.get("/api/developers"),
  getById: (id) => api.get(`/api/developers/${id}`),
};

// ── Badges ─────────────────────────────────────────────────────────────────
export const badgesApi = {
  getByDeveloper: (devId) => api.get(`/api/badges/${devId}`),
  evaluate:       (devId) => api.post(`/api/badges/evaluate/${devId}`),
};

// ── Notifications ──────────────────────────────────────────────────────────
export const notificationsApi = {
  getByUser: (userId) => api.get(`/api/notifications/${userId}`),
  markRead:  (id)     => api.put(`/api/notifications/${id}/read`),
};

export default api;
