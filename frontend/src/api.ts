import axios, { AxiosError } from "axios";
import type { AuthResponse } from "./types";

const TOKEN_KEY = "invictus_token";
const SESSION_KEY = "invictus_session";

export const api = axios.create({ baseURL: `${import.meta.env.VITE_API_URL ?? ""}/api` });

export function loadSession(): AuthResponse | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY);
    return raw ? (JSON.parse(raw) as AuthResponse) : null;
  } catch {
    return null;
  }
}
export function saveSession(s: AuthResponse) {
  localStorage.setItem(TOKEN_KEY, s.token);
  localStorage.setItem(SESSION_KEY, JSON.stringify(s));
}
export function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(SESSION_KEY);
}

api.interceptors.request.use((cfg) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) cfg.headers.Authorization = `Bearer ${token}`;
  return cfg;
});

// An expired or invalid token sends the user back to the right login page.
api.interceptors.response.use(
  (r) => r,
  (err: AxiosError) => {
    const url = err.config?.url ?? "";
    if (err.response?.status === 401 && !url.startsWith("/auth/")) {
      const admin = window.location.pathname.startsWith("/admin");
      clearSession();
      window.location.assign(admin ? "/admin/login" : "/login");
    }
    return Promise.reject(err);
  },
);

export function errorMessage(e: unknown): string {
  if (axios.isAxiosError(e)) {
    const data = e.response?.data as { message?: string } | undefined;
    if (data?.message) return data.message;
    if (!e.response) return "Cannot reach the server. Check your connection and try again.";
  }
  return "Something went wrong. Please try again.";
}

export function statusOf(e: unknown): number | undefined {
  return axios.isAxiosError(e) ? e.response?.status : undefined;
}
