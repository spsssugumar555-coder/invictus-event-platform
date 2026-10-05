import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from "react";
import { Navigate } from "react-router-dom";
import { api, clearSession, loadSession, saveSession } from "./api";
import type { AuthResponse, Role } from "./types";

interface RegisterInput {
  teamName: string; captainName: string; captainEmail: string; members: string[]; password: string;
}
interface AuthCtx {
  session: AuthResponse | null;
  login: (email: string, password: string, expected: Role) => Promise<void>;
  register: (input: RegisterInput) => Promise<void>;
  logout: () => void;
}

const Ctx = createContext<AuthCtx | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<AuthResponse | null>(() => loadSession());

  const login = useCallback(async (email: string, password: string, expected: Role) => {
    const { data } = await api.post<AuthResponse>("/auth/login", { email, password });
    if (data.role !== expected) {
      throw new Error(expected === "ADMIN" ? "This account is not an admin account." : "Admins sign in from the admin login page.");
    }
    saveSession(data);
    setSession(data);
  }, []);

  const register = useCallback(async (input: RegisterInput) => {
    const { data } = await api.post<AuthResponse>("/auth/register", input);
    saveSession(data);
    setSession(data);
  }, []);

  const logout = useCallback(() => {
    clearSession();
    setSession(null);
  }, []);

  const value = useMemo(() => ({ session, login, register, logout }), [session, login, register, logout]);
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useAuth(): AuthCtx {
  const ctx = useContext(Ctx);
  if (!ctx) throw new Error("useAuth must be used inside AuthProvider");
  return ctx;
}

/** UI guard only. The real enforcement happens in the backend. */
export function RequireRole({ role, children }: { role: Role; children: ReactNode }) {
  const { session } = useAuth();
  if (!session) return <Navigate to={role === "ADMIN" ? "/admin/login" : "/login"} replace />;
  if (session.role !== role) return <Navigate to={session.role === "ADMIN" ? "/admin/dashboard" : "/dashboard"} replace />;
  return <>{children}</>;
}
