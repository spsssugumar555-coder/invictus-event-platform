import { useState, type FormEvent } from "react";
import axios from "axios";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { useAuth } from "../auth";
import { errorMessage } from "../api";
import AuthShell from "../components/AuthShell";
import { ErrorNote } from "../components/ui";

export default function Login() {
  const { session, login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (session?.role === "PLAYER") return <Navigate to="/dashboard" replace />;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(email, password, "PLAYER");
      navigate("/dashboard");
    } catch (err) {
      setError(!axios.isAxiosError(err) && err instanceof Error ? err.message : errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell title="Welcome back" subtitle="Sign in with your captain email to continue your run."
      footer={<>
        New team? <Link className="font-semibold text-brass hover:underline" to="/register">Register here</Link>
        <span className="mx-2">·</span>
        <Link className="hover:text-paper" to="/admin/login">Organiser login</Link>
      </>}>
      <form onSubmit={submit} className="space-y-4">
        {error && <ErrorNote>{error}</ErrorNote>}
        <div>
          <label className="label" htmlFor="email">Captain email</label>
          <input id="email" type="email" required autoComplete="username" className="field" value={email} onChange={(e) => setEmail(e.target.value)} />
        </div>
        <div>
          <label className="label" htmlFor="password">Password</label>
          <input id="password" type="password" required autoComplete="current-password" className="field" value={password} onChange={(e) => setPassword(e.target.value)} />
        </div>
        <button className="btn-primary w-full" disabled={busy}>{busy ? "Signing in…" : "Sign in"}</button>
      </form>
    </AuthShell>
  );
}
