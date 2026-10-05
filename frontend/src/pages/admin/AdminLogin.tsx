import { useState, type FormEvent } from "react";
import axios from "axios";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { useAuth } from "../../auth";
import { errorMessage } from "../../api";
import { Brand, ErrorNote } from "../../components/ui";

export default function AdminLogin() {
  const { session, login } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (session?.role === "ADMIN") return <Navigate to="/admin/dashboard" replace />;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login(email, password, "ADMIN");
      navigate("/admin/dashboard");
    } catch (err) {
      setError(!axios.isAxiosError(err) && err instanceof Error ? err.message : errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="grid min-h-screen place-items-center px-4">
      <div className="w-full max-w-sm card">
        <Brand />
        <h1 className="mt-6 text-2xl font-bold">Organiser sign in</h1>
        <p className="mb-5 mt-1 text-sm text-mist">Admin accounts are created by the server, not by registration.</p>
        <form onSubmit={submit} className="space-y-4">
          {error && <ErrorNote>{error}</ErrorNote>}
          <div>
            <label className="label" htmlFor="email">Email</label>
            <input id="email" type="email" required autoComplete="username" className="field" value={email} onChange={(e) => setEmail(e.target.value)} />
          </div>
          <div>
            <label className="label" htmlFor="password">Password</label>
            <input id="password" type="password" required autoComplete="current-password" className="field" value={password} onChange={(e) => setPassword(e.target.value)} />
          </div>
          <button className="btn-primary w-full" disabled={busy}>{busy ? "Signing in…" : "Sign in"}</button>
        </form>
        <p className="mt-5 text-sm text-mist"><Link className="hover:text-paper" to="/login">Back to team login</Link></p>
      </div>
    </div>
  );
}
