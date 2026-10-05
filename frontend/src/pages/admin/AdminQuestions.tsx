import { useState, type FormEvent } from "react";
import { api, errorMessage } from "../../api";
import { useLoad } from "../../hooks";
import type { AdminQuestion, AdminQuestionSet } from "../../types";
import Crossword from "../../components/Crossword";
import { Badge, Empty, ErrorNote, PageHeader, Spinner, TableShell } from "../../components/ui";

export default function AdminQuestions() {
  const { data, error, loading, setData } = useLoad(async () => (await api.get<AdminQuestionSet>("/admin/round1/questions")).data);
  const [clue, setClue] = useState("");
  const [answer, setAnswer] = useState("");
  const [editing, setEditing] = useState<AdminQuestion | null>(null);
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);

  if (loading) return <Spinner />;
  if (error || !data) return <ErrorNote>{error ?? "Could not load questions."}</ErrorNote>;

  const locked = data.published;

  async function run(fn: () => Promise<void>, ok?: string) {
    setBusy(true);
    setMsg(null);
    try {
      await fn();
      if (ok) setMsg({ ok: true, text: ok });
    } catch (e) {
      setMsg({ ok: false, text: errorMessage(e) });
    } finally {
      setBusy(false);
    }
  }
  const reload = async () => setData((await api.get<AdminQuestionSet>("/admin/round1/questions")).data);

  async function save(e: FormEvent) {
    e.preventDefault();
    await run(async () => {
      if (editing) await api.put(`/admin/round1/questions/${editing.id}`, { clue, answer });
      else await api.post("/admin/round1/questions", { clue, answer });
      setClue(""); setAnswer(""); setEditing(null);
      await reload();
    });
  }

  function edit(q: AdminQuestion) {
    setEditing(q); setClue(q.clue); setAnswer(q.answer);
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  async function remove(q: AdminQuestion) {
    if (!window.confirm(`Delete question ${q.number} (${q.answer})?`)) return;
    await run(async () => { await api.delete(`/admin/round1/questions/${q.id}`); await reload(); });
  }

  async function move(index: number, delta: number) {
    const ids = data!.questions.map((q) => q.id);
    const j = index + delta;
    if (j < 0 || j >= ids.length) return;
    [ids[index], ids[j]] = [ids[j], ids[index]];
    await run(async () => setData((await api.put<AdminQuestionSet>("/admin/round1/questions/order", { questionIds: ids })).data));
  }

  const post = (path: string, ok: string) => run(async () => setData((await api.post<AdminQuestionSet>(path)).data), ok);

  return (
    <>
      <PageHeader title="Round 1 questions" subtitle="Write the clues and answers. The crossword is generated from your answers. Players never receive the answers."
        action={locked ? <Badge tone="good">Published</Badge> : <Badge tone="warn">Draft</Badge>} />

      {msg && <div className="mb-4">{msg.ok ? <p role="status" className="rounded-lg border border-mint/40 bg-mint/10 px-4 py-3 text-sm text-mint">{msg.text}</p> : <ErrorNote>{msg.text}</ErrorNote>}</div>}

      {!locked && (
        <form onSubmit={save} className="card mb-6 grid gap-4 md:grid-cols-[1fr_16rem_auto] md:items-end">
          <div>
            <label className="label" htmlFor="clue">{editing ? `Editing question ${editing.number}` : "New question: clue"}</label>
            <input id="clue" required maxLength={500} className="field" value={clue} onChange={(e) => setClue(e.target.value)} placeholder="Language used to style web pages" />
          </div>
          <div>
            <label className="label" htmlFor="answer">Answer</label>
            <input id="answer" required maxLength={40} className="field uppercase" value={answer} onChange={(e) => setAnswer(e.target.value)} placeholder="CSS" />
          </div>
          <div className="flex gap-2">
            <button className="btn-primary" disabled={busy}>{editing ? "Save" : "Add question"}</button>
            {editing && <button type="button" className="btn-ghost" onClick={() => { setEditing(null); setClue(""); setAnswer(""); }}>Cancel</button>}
          </div>
        </form>
      )}
      {locked && <p className="mb-4 text-sm text-mist">The set is published and frozen. {data.sessionsExist ? "Teams have started, so it cannot be unpublished." : "Unpublish to make changes."}</p>}

      {data.questions.length === 0 ? <Empty>No questions yet. Add the first one above.</Empty> : (
        <TableShell>
          <thead><tr>{["#", "Clue", "Answer", "In crossword", ""].map((h, i) => <th key={i} className="th">{h}</th>)}</tr></thead>
          <tbody className="divide-y divide-edge">
            {data.questions.map((q, i) => (
              <tr key={q.id} className="hover:bg-raised/40">
                <td className="td tabular-nums font-semibold">{q.number}</td>
                <td className="td max-w-md whitespace-normal">{q.clue}</td>
                <td className="td font-display font-bold tracking-wider text-brass">{q.answer}</td>
                <td className="td text-mist">{q.displayNumber ? `${q.displayNumber} ${q.direction === "DOWN" ? "Down" : "Across"}` : "Not generated"}</td>
                <td className="td">
                  {!locked && (
                    <div className="flex gap-1">
                      <button className="btn-ghost !px-2.5 !py-1" aria-label={`Move question ${q.number} up`} disabled={busy || i === 0} onClick={() => void move(i, -1)}>Up</button>
                      <button className="btn-ghost !px-2.5 !py-1" aria-label={`Move question ${q.number} down`} disabled={busy || i === data.questions.length - 1} onClick={() => void move(i, 1)}>Down</button>
                      <button className="btn-ghost !px-2.5 !py-1" onClick={() => edit(q)}>Edit</button>
                      <button className="btn-ghost !px-2.5 !py-1 text-coral" onClick={() => void remove(q)}>Delete</button>
                    </div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </TableShell>
      )}

      <section className="card mt-6">
        <div className="mb-4 flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="text-xl font-bold">Crossword</h2>
            <p className="text-sm text-mist">{data.layoutReady ? `${data.rows} rows by ${data.cols} columns. This exact layout is what every team sees.` : "Generate the layout to preview it. Letters are shown here for you only."}</p>
          </div>
          {!locked && (
            <div className="flex flex-wrap gap-2">
              <button className="btn-ghost" disabled={busy || data.questions.length === 0} onClick={() => void post("/admin/round1/crossword/generate", "Crossword generated.")}>Generate preview</button>
              <button className="btn-primary" disabled={busy || data.questions.length === 0}
                onClick={() => { if (window.confirm("Publish the questions and crossword? Teams can start Round 1 and the set will be frozen.")) void post("/admin/round1/publish", "Published. Teams can now start Round 1."); }}>Publish round 1</button>
            </div>
          )}
          {locked && !data.sessionsExist && <button className="btn-danger" disabled={busy} onClick={() => void post("/admin/round1/unpublish", "Unpublished.")}>Unpublish</button>}
        </div>
        {data.crossword ? <Crossword grid={data.crossword} /> : <p className="py-8 text-center text-mist">No layout yet.</p>}
      </section>
    </>
  );
}
