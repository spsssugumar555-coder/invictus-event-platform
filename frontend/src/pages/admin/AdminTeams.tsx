import { useMemo, useState, type FormEvent } from "react";
import { api, errorMessage } from "../../api";
import { useLoad } from "../../hooks";
import type { AdminTeam } from "../../types";
import Modal from "../../components/Modal";
import { Badge, Empty, ErrorNote, PageHeader, Spinner, TableShell, pretty, statusTone } from "../../components/ui";

const MAX_EXTRA = 4;

interface FormState { teamName: string; captainName: string; captainEmail: string; members: string[]; password: string }
const emptyForm: FormState = { teamName: "", captainName: "", captainEmail: "", members: [""], password: "" };

function TeamForm({ team, onDone, onCancel }: { team: AdminTeam | null; onDone: () => void; onCancel: () => void }) {
  const editing = team !== null;
  const [f, setF] = useState<FormState>(team
    ? { teamName: team.name, captainName: team.captainName, captainEmail: team.captainEmail, members: team.members.length ? team.members : [""], password: "" }
    : emptyForm);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    const members = f.members.map((m) => m.trim()).filter(Boolean);
    try {
      if (editing) {
        // password is only sent when the organizer typed a new one
        await api.put(`/admin/teams/${team.id}`, { teamName: f.teamName, captainName: f.captainName, captainEmail: f.captainEmail, members, ...(f.password ? { password: f.password } : {}) });
      } else {
        await api.post("/admin/teams", { teamName: f.teamName, captainName: f.captainName, captainEmail: f.captainEmail, members, password: f.password });
      }
      onDone();
    } catch (err) {
      setError(errorMessage(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="space-y-4">
      {error && <ErrorNote>{error}</ErrorNote>}
      <div>
        <label className="label" htmlFor="tm-name">Team name</label>
        <input id="tm-name" required maxLength={100} className="field" value={f.teamName} onChange={(e) => setF({ ...f, teamName: e.target.value })} />
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <label className="label" htmlFor="tm-captain">Captain name</label>
          <input id="tm-captain" required maxLength={100} className="field" value={f.captainName} onChange={(e) => setF({ ...f, captainName: e.target.value })} />
        </div>
        <div>
          <label className="label" htmlFor="tm-email">Captain email (sign-in)</label>
          <input id="tm-email" type="email" required className="field" value={f.captainEmail} onChange={(e) => setF({ ...f, captainEmail: e.target.value })} />
        </div>
      </div>
      <fieldset>
        <legend className="label">Other members <span className="text-mist/70">(up to {MAX_EXTRA})</span></legend>
        <div className="space-y-2">
          {f.members.map((m, i) => (
            <div key={i} className="flex gap-2">
              <input aria-label={`Member ${i + 1} name`} maxLength={100} className="field" value={m}
                onChange={(e) => setF({ ...f, members: f.members.map((x, j) => (j === i ? e.target.value : x)) })} />
              {f.members.length > 1 && <button type="button" className="btn-ghost" onClick={() => setF({ ...f, members: f.members.filter((_, j) => j !== i) })}>Remove</button>}
            </div>
          ))}
        </div>
        {f.members.length < MAX_EXTRA && <button type="button" className="mt-2 text-sm font-semibold text-brass hover:underline" onClick={() => setF({ ...f, members: [...f.members, ""] })}>Add member</button>}
      </fieldset>
      <div>
        <label className="label" htmlFor="tm-pw">{editing ? "New password (leave blank to keep the current one)" : "Password"}</label>
        <input id="tm-pw" type="password" autoComplete="new-password" minLength={8} maxLength={72} required={!editing} className="field" value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} />
        <p className="mt-1 text-xs text-mist">At least 8 characters. Share it with the team captain.</p>
      </div>
      {editing && <p className="rounded-lg bg-raised px-3 py-2 text-xs text-mist">Editing never changes Round 1 progress, the question sequence, qualification or Round 2 data.</p>}
      <div className="flex justify-end gap-3 pt-2">
        <button type="button" className="btn-ghost" onClick={onCancel}>Cancel</button>
        <button className="btn-primary" disabled={busy}>{busy ? "Saving…" : editing ? "Save changes" : "Create team"}</button>
      </div>
    </form>
  );
}

function DeleteConfirm({ team, onDone, onCancel }: { team: AdminTeam; onDone: () => void; onCancel: () => void }) {
  const [typed, setTyped] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function remove() {
    setBusy(true);
    setError(null);
    try {
      await api.delete(`/admin/teams/${team.id}`);
      onDone();
    } catch (err) {
      setError(errorMessage(err));
      setBusy(false);
    }
  }

  return (
    <div className="space-y-4">
      {error && <ErrorNote>{error}</ErrorNote>}
      <p>You are about to permanently delete <strong className="text-brass">{team.name}</strong> (captain {team.captainName}).</p>
      <ul className="list-disc space-y-1 pl-5 text-sm text-mist">
        <li>The team's login is removed.</li>
        <li>All Round 1 progress, attempts and its question sequence are removed.</li>
        <li>Any qualification, Round 2 submission, draft and evaluation are removed.</li>
        <li>Other teams are not affected. This cannot be undone.</li>
      </ul>
      <div>
        <label className="label" htmlFor="del-confirm">Type the team name to confirm</label>
        <input id="del-confirm" autoComplete="off" className="field" value={typed} onChange={(e) => setTyped(e.target.value)} placeholder={team.name} />
      </div>
      <div className="flex justify-end gap-3">
        <button className="btn-ghost" onClick={onCancel}>Cancel</button>
        <button className="btn-danger" disabled={busy || typed.trim() !== team.name} onClick={() => void remove()}>{busy ? "Deleting…" : "Delete team"}</button>
      </div>
    </div>
  );
}

export default function AdminTeams() {
  const { data, error, loading, reload } = useLoad(async () => (await api.get<AdminTeam[]>("/admin/teams")).data);
  const [q, setQ] = useState("");
  const [filter, setFilter] = useState("ALL");
  const [dialog, setDialog] = useState<{ kind: "create" } | { kind: "edit"; team: AdminTeam } | { kind: "delete"; team: AdminTeam } | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const rows = useMemo(() => {
    const needle = q.trim().toLowerCase();
    return (data ?? []).filter((t) =>
      (filter === "ALL" || t.qualification === filter) &&
      (!needle || [t.name, t.captainName, t.captainEmail, ...t.members].some((x) => x.toLowerCase().includes(needle))));
  }, [data, q, filter]);

  if (loading && !data) return <Spinner />;
  if (error || !data) return <ErrorNote>{error ?? "Could not load teams."}</ErrorNote>;

  const done = (msg: string) => { setDialog(null); setNotice(msg); void reload(); };

  return (
    <>
      <PageHeader title="Teams" subtitle={`${data.length} registered. Add, edit or remove teams here.`}
        action={<button className="btn-primary" onClick={() => { setNotice(null); setDialog({ kind: "create" }); }}>Add team</button>} />
      {notice && <p role="status" className="mb-4 rounded-lg border border-mint/40 bg-mint/10 px-4 py-3 text-sm text-mint">{notice}</p>}
      <div className="mb-4 flex flex-wrap gap-3">
        <input aria-label="Search teams" className="field max-w-sm" placeholder="Search team, captain, member or email" value={q} onChange={(e) => setQ(e.target.value)} />
        <select aria-label="Filter by qualification" className="field w-auto" value={filter} onChange={(e) => setFilter(e.target.value)}>
          <option value="ALL">All teams</option>
          <option value="PENDING">Pending</option>
          <option value="QUALIFIED">Qualified</option>
          <option value="NOT_QUALIFIED">Not qualified</option>
        </select>
      </div>
      {rows.length === 0 ? <Empty>{data.length === 0 ? "No teams yet. Use Add team to create the first one." : "No teams match your search."}</Empty> : (
        <TableShell>
          <thead><tr>{["Team", "Captain", "Members", "Round 1", "Score", "Rank", "Qualification", "Round 2", ""].map((h, i) => <th key={i} className="th">{h}</th>)}</tr></thead>
          <tbody className="divide-y divide-edge">
            {rows.map((t) => (
              <tr key={t.id} className="hover:bg-raised/40">
                <td className="td font-semibold">{t.name}</td>
                <td className="td">{t.captainName}<div className="text-xs text-mist">{t.captainEmail}</div></td>
                <td className="td max-w-xs truncate" title={t.members.join(", ")}>{t.members.length ? t.members.join(", ") : "None"}</td>
                <td className="td"><Badge tone={statusTone(t.round1Status)}>{pretty(t.round1Status)}</Badge></td>
                <td className="td tabular-nums">{t.score ?? "None"}</td>
                <td className="td tabular-nums">{t.rank ?? "None"}</td>
                <td className="td"><Badge tone={statusTone(t.qualification)}>{pretty(t.qualification)}</Badge></td>
                <td className="td"><Badge tone={statusTone(t.round2Status === "EVALUATED" ? "COMPLETED" : t.round2Status)}>{pretty(t.round2Status)}</Badge></td>
                <td className="td">
                  <div className="flex gap-1">
                    <button className="btn-ghost !px-2.5 !py-1" aria-label={`Edit ${t.name}`} onClick={() => { setNotice(null); setDialog({ kind: "edit", team: t }); }}>Edit</button>
                    <button className="btn-ghost !px-2.5 !py-1 text-coral" aria-label={`Delete ${t.name}`} onClick={() => { setNotice(null); setDialog({ kind: "delete", team: t }); }}>Delete</button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </TableShell>
      )}

      {dialog?.kind === "create" && (
        <Modal title="Add team" onClose={() => setDialog(null)}>
          <TeamForm team={null} onCancel={() => setDialog(null)} onDone={() => done("Team created. It starts with no Round 1 progress and is not qualified.")} />
        </Modal>
      )}
      {dialog?.kind === "edit" && (
        <Modal title={`Edit ${dialog.team.name}`} onClose={() => setDialog(null)}>
          <TeamForm team={dialog.team} onCancel={() => setDialog(null)} onDone={() => done("Team updated. Progress was not changed.")} />
        </Modal>
      )}
      {dialog?.kind === "delete" && (
        <Modal title="Delete team" onClose={() => setDialog(null)}>
          <DeleteConfirm team={dialog.team} onCancel={() => setDialog(null)} onDone={() => done(`Deleted team ${dialog.team.name}.`)} />
        </Modal>
      )}
    </>
  );
}
