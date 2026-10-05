import { useState, type FormEvent } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { useAuth } from "../auth";
import { errorMessage } from "../api";
import AuthShell from "../components/AuthShell";
import { ErrorNote } from "../components/ui";

const MAX_EXTRA = 4;

export default function Register() {
  const { session, register } = useAuth();
  const navigate = useNavigate();
  const [teamName, setTeamName] = useState("");
  const [captainName, setCaptainName] = useState("");
  const [captainEmail, setCaptainEmail] = useState("");
  const [members, setMembers] = useState<string[]>([""]);
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  if (session?.role === "PLAYER") return <Navigate to="/dashboard" replace />;

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    if (password !== confirm) {
      setError("Passwords do not match.");
      return;
    }
    setBusy(true);
    try {
      await register({ teamName, captainName, captainEmail, members: members.map((m) => m.trim()).filter(Boolean), password });
      navigate("/dashboard");
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <AuthShell title="Register your team" subtitle="One account per team. The captain signs in for everyone."
      footer={<>Already registered? <Link className="font-semibold text-brass hover:underline" to="/login">Sign in</Link></>}>
      <form onSubmit={submit} className="space-y-4">
        {error && <ErrorNote>{error}</ErrorNote>}
        <div>
          <label className="label" htmlFor="teamName">Team name</label>
          <input id="teamName" required maxLength={100} className="field" value={teamName} onChange={(e) => setTeamName(e.target.value)} />
        </div>
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label className="label" htmlFor="captainName">Captain name</label>
            <input id="captainName" required maxLength={100} className="field" value={captainName} onChange={(e) => setCaptainName(e.target.value)} />
          </div>
          <div>
            <label className="label" htmlFor="captainEmail">Captain email</label>
            <input id="captainEmail" type="email" required autoComplete="username" className="field" value={captainEmail} onChange={(e) => setCaptainEmail(e.target.value)} />
          </div>
        </div>
        <fieldset>
          <legend className="label">Team members <span className="text-mist/70">(up to {MAX_EXTRA}, besides the captain)</span></legend>
          <div className="space-y-2">
            {members.map((m, i) => (
              <div key={i} className="flex gap-2">
                <input aria-label={`Member ${i + 1} name`} maxLength={100} className="field" placeholder={`Member ${i + 1}`} value={m}
                  onChange={(e) => setMembers(members.map((x, j) => (j === i ? e.target.value : x)))} />
                {members.length > 1 && (
                  <button type="button" className="btn-ghost" aria-label={`Remove member ${i + 1}`} onClick={() => setMembers(members.filter((_, j) => j !== i))}>Remove</button>
                )}
              </div>
            ))}
          </div>
          {members.length < MAX_EXTRA && (
            <button type="button" className="mt-2 text-sm font-semibold text-brass hover:underline" onClick={() => setMembers([...members, ""])}>Add another member</button>
          )}
        </fieldset>
        <div className="grid gap-4 sm:grid-cols-2">
          <div>
            <label className="label" htmlFor="password">Password</label>
            <input id="password" type="password" required minLength={8} maxLength={72} autoComplete="new-password" className="field" value={password} onChange={(e) => setPassword(e.target.value)} />
          </div>
          <div>
            <label className="label" htmlFor="confirm">Confirm password</label>
            <input id="confirm" type="password" required minLength={8} maxLength={72} autoComplete="new-password" className="field" value={confirm} onChange={(e) => setConfirm(e.target.value)} />
          </div>
        </div>
        <p className="text-xs text-mist">At least 8 characters.</p>
        <button className="btn-primary w-full" disabled={busy}>{busy ? "Creating team…" : "Create team"}</button>
      </form>
    </AuthShell>
  );
}
