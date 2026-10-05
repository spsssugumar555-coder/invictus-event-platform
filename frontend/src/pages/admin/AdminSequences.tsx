import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api, errorMessage } from "../../api";
import { useLoad } from "../../hooks";
import type { TeamSequenceRow } from "../../types";
import { Badge, Empty, ErrorNote, PageHeader, Spinner, TableShell } from "../../components/ui";

export default function AdminSequences() {
  const { data, error, loading, setData } = useLoad(async () => (await api.get<TeamSequenceRow[]>("/admin/round1/teams")).data);
  const published = useLoad(async () => (await api.get<{ published: boolean }>("/admin/round1/questions")).data.published);
  const [drafts, setDrafts] = useState<Record<number, string>>({});
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);

  useEffect(() => {
    if (data) setDrafts(Object.fromEntries(data.map((t) => [t.teamId, t.questionNumbers.join(", ")])));
  }, [data]);

  if (loading) return <Spinner />;
  if (error || !data) return <ErrorNote>{error ?? "Could not load teams."}</ErrorNote>;

  async function save(t: TeamSequenceRow) {
    const nums = (drafts[t.teamId] ?? "").split(/[,\s]+/).filter(Boolean).map(Number);
    if (nums.some((n) => !Number.isInteger(n))) {
      setMsg({ ok: false, text: "Use question numbers separated by commas, for example 5, 12, 3." });
      return;
    }
    setBusy(true);
    setMsg(null);
    try {
      setData((await api.post<TeamSequenceRow[]>("/admin/round1/team-sequence", { teamId: t.teamId, questionNumbers: nums })).data);
      setMsg({ ok: true, text: `Saved the sequence for ${t.teamName}.` });
    } catch (e) {
      setMsg({ ok: false, text: errorMessage(e) });
    } finally {
      setBusy(false);
    }
  }

  async function auto() {
    if (!window.confirm("Give every team that has not started a full, staggered order? This replaces their current sequence.")) return;
    setBusy(true);
    setMsg(null);
    try {
      setData((await api.post<TeamSequenceRow[]>("/admin/round1/team-sequence/auto")).data);
      setMsg({ ok: true, text: "Staggered sequences assigned." });
    } catch (e) {
      setMsg({ ok: false, text: errorMessage(e) });
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader title="Team sequences" subtitle="Choose which questions each team sees first, by question number (for example 5, 12, 3, 18). Any questions you leave out follow in an automatic order. A sequence locks once the team starts Round 1."
        action={<button className="btn-ghost" disabled={busy} onClick={() => void auto()}>Auto-assign staggered</button>} />
      {published.data === false && (
        <div role="alert" className="mb-4 rounded-xl border border-brass/50 bg-brass/10 px-4 py-3 text-sm text-brass">
          Sequences are saved, but Round 1 is <strong>not published</strong>, so teams cannot start yet.
          Publish it on the <Link className="font-semibold underline" to="/admin/questions">Questions</Link> page.
        </div>
      )}
      {msg && <div className="mb-4">{msg.ok ? <p role="status" className="rounded-lg border border-mint/40 bg-mint/10 px-4 py-3 text-sm text-mint">{msg.text}</p> : <ErrorNote>{msg.text}</ErrorNote>}</div>}
      {data.length === 0 ? <Empty>No teams have registered yet.</Empty> : (
        <TableShell>
          <thead><tr>{["Team", "Round 1", "First questions", ""].map((h, i) => <th key={i} className="th">{h}</th>)}</tr></thead>
          <tbody className="divide-y divide-edge">
            {data.map((t) => (
              <tr key={t.teamId}>
                <td className="td font-semibold">{t.teamName}</td>
                <td className="td">{t.sessionStarted ? <Badge tone="warn">Started (locked)</Badge> : <Badge>Not started</Badge>}</td>
                <td className="td">
                  <input aria-label={`Sequence for ${t.teamName}`} className="field !py-1.5 min-w-[14rem]" disabled={t.sessionStarted || busy} placeholder="Automatic"
                    value={drafts[t.teamId] ?? ""} onChange={(e) => setDrafts({ ...drafts, [t.teamId]: e.target.value })} />
                </td>
                <td className="td"><button className="btn-ghost !py-1.5" disabled={t.sessionStarted || busy} onClick={() => void save(t)}>Save</button></td>
              </tr>
            ))}
          </tbody>
        </TableShell>
      )}
    </>
  );
}
