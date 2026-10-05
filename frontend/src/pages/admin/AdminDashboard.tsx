import { useState } from "react";
import { Link } from "react-router-dom";
import { api, errorMessage } from "../../api";
import { useLoad } from "../../hooks";
import type { AdminStats } from "../../types";
import { Badge, ErrorNote, PageHeader, Spinner, Stat } from "../../components/ui";

export default function AdminDashboard() {
  const { data: s, error, loading, reload } = useLoad(async () => (await api.get<AdminStats>("/admin/dashboard")).data);
  const published = useLoad(async () => (await api.get<{ published: boolean }>("/admin/round1/questions")).data.published);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const [err, setErr] = useState<string | null>(null);

  if (loading) return <Spinner />;
  if (error || !s) return <ErrorNote>{error ?? "Could not load statistics."}</ErrorNote>;

  async function finalize() {
    if (!window.confirm("End Round 1 and publish qualification? Only teams that solved every clue can qualify, and the top 10 of them do. Teams can no longer answer questions afterwards.")) return;
    setBusy(true);
    setErr(null);
    try {
      await api.post("/admin/qualifications/finalize");
      setMsg("Round 1 closed. Qualification published.");
      await reload();
    } catch (e) {
      setErr(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader title="Overview" subtitle="Live numbers for the whole event."
        action={s.round1Closed ? <Badge tone="bad">Round 1 closed</Badge>
          : published.data === false ? <Badge tone="warn">Round 1 not published</Badge>
          : <Badge tone="good">Round 1 open</Badge>} />
      {!s.round1Closed && published.data === false && (
        <div role="alert" className="mb-6 rounded-xl border border-brass/50 bg-brass/10 px-4 py-3 text-sm text-brass">
          Round 1 is not published, so every team sees "Round 1 hasn't opened yet" and cannot start.
          Go to <Link className="font-semibold underline" to="/admin/questions">Questions</Link> and click <strong>Publish round 1</strong>.
        </div>
      )}
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        <Stat label="Teams" value={s.totalTeams} />
        <Stat label="Participants" value={s.totalParticipants} hint="Captains plus members" />
        <Stat label="Round 1 active" value={s.round1Active} />
        <Stat label="Round 1 completed" value={s.round1Completed} />
        <Stat label="Qualified teams" value={s.qualifiedTeams} />
        <Stat label="Round 2 submissions" value={s.round2Submissions} />
        <Stat label="Evaluations completed" value={s.evaluationsCompleted} />
      </div>

      <section className="card mt-8 max-w-2xl">
        <h2 className="text-xl font-bold">Close Round 1</h2>
        <p className="mt-1 text-sm text-mist">Ranks teams that completed Round 1 (score, then earliest completion), stores the top 10 as qualified, and locks Round 1. This cannot be undone from the console.</p>
        {msg && <p className="mt-3 text-sm text-mint" role="status">{msg}</p>}
        {err && <div className="mt-3"><ErrorNote>{err}</ErrorNote></div>}
        <button className="btn-danger mt-4" onClick={finalize} disabled={busy || s.round1Closed}>
          {s.round1Closed ? "Round 1 already closed" : busy ? "Publishing…" : "Close Round 1 and publish top 10"}
        </button>
      </section>
    </>
  );
}
