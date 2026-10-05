import { useEffect, useRef, useState, type FormEvent } from "react";
import { api, errorMessage } from "../../api";
import { useBlobUrl, useLoad } from "../../hooks";
import type { AdminChallenge, AdminResultRow, AdminSubmissionDetail, Evaluation } from "../../types";
import PreviewFrame from "../../components/PreviewFrame";
import { Badge, Empty, ErrorNote, PageHeader, Spinner, TableShell, pretty, statusTone } from "../../components/ui";

const CRITERIA: { key: keyof Evaluation; label: string; weight: number }[] = [
  { key: "requirementScore", label: "Requirement completion", weight: 25 },
  { key: "functionalityScore", label: "Functionality", weight: 20 },
  { key: "uiScore", label: "Visual match to reference", weight: 15 },
  { key: "responsivenessScore", label: "Responsiveness", weight: 10 },
  { key: "technicalScore", label: "Code quality", weight: 15 },
  { key: "accuracyScore", label: "Overall fidelity", weight: 15 },
];

function EvaluationPanel({ ev }: { ev: Evaluation | null }) {
  const [raw, setRaw] = useState(false);
  if (!ev) return <p className="text-sm text-mist">Not evaluated yet.</p>;
  return (
    <div className="space-y-4">
      <div className="flex flex-wrap items-center gap-3">
        <Badge tone={statusTone(ev.status)}>{pretty(ev.status)}</Badge>
        {ev.finalScore !== null && <span className="font-display text-3xl font-extrabold text-brass">{ev.finalScore}<span className="text-base font-medium text-mist"> / 100</span></span>}
      </div>
      {ev.errorMessage && <ErrorNote>{ev.errorMessage}</ErrorNote>}
      {ev.finalScore !== null && (
        <ul className="space-y-2.5">
          {CRITERIA.map((c) => {
            const v = ev[c.key] as number | null;
            return (
              <li key={c.key}>
                <div className="mb-1 flex justify-between text-sm"><span>{c.label} <span className="text-mist">(weight {c.weight})</span></span><span className="font-semibold tabular-nums">{v ?? 0}/10</span></div>
                <div className="h-2 overflow-hidden rounded-full bg-raised"><div className="h-full rounded-full bg-brass" style={{ width: `${(v ?? 0) * 10}%` }} /></div>
              </li>
            );
          })}
        </ul>
      )}
      {ev.feedback && <div><div className="mb-1 text-sm text-mist">Feedback</div><p className="whitespace-pre-line text-sm leading-relaxed">{ev.feedback}</p></div>}
      {ev.rawResponse && (
        <div>
          <button className="text-sm font-semibold text-brass hover:underline" onClick={() => setRaw(!raw)}>{raw ? "Hide" : "Show"} raw Gemini response</button>
          {raw && <pre className="mt-2 max-h-48 overflow-auto rounded-lg bg-ink p-3 text-xs">{ev.rawResponse}</pre>}
        </div>
      )}
      {ev.evaluatedAt && <p className="text-xs text-mist">Evaluated {new Date(ev.evaluatedAt).toLocaleString()}</p>}
    </div>
  );
}

function ChallengeSetup({ initial, onChange }: { initial: AdminChallenge; onChange: (c: AdminChallenge) => void }) {
  const [c, setC] = useState(initial);
  const [form, setForm] = useState({ title: initial.title, prompt: initial.prompt, requirements: initial.requirements ?? "", evaluationPrompt: initial.evaluationPrompt ?? "", timeLimitMinutes: initial.timeLimitMinutes });
  const [busy, setBusy] = useState(false);
  const [msg, setMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [imgVersion, setImgVersion] = useState(0);
  const fileRef = useRef<HTMLInputElement>(null);
  const image = useBlobUrl(c.hasReferenceImage ? "/admin/round2/reference-image" : null, imgVersion);
  const locked = c.published;

  useEffect(() => onChange(c), [c, onChange]);

  async function run(fn: () => Promise<AdminChallenge>, ok: string) {
    setBusy(true);
    setMsg(null);
    try {
      setC(await fn());
      setMsg({ ok: true, text: ok });
    } catch (e) {
      setMsg({ ok: false, text: errorMessage(e) });
    } finally {
      setBusy(false);
    }
  }

  const save = (e: FormEvent) => { e.preventDefault(); void run(async () => (await api.put<AdminChallenge>("/admin/round2/challenge", form)).data, "Saved."); };

  async function upload(file: File | undefined) {
    if (!file) return;
    const body = new FormData();
    body.append("file", file);
    await run(async () => {
      const r = (await api.post<AdminChallenge>("/admin/round2/reference-image", body)).data;
      setImgVersion((v) => v + 1);
      return r;
    }, "Reference image uploaded.");
    if (fileRef.current) fileRef.current.value = "";
  }

  return (
    <div className="grid gap-6 xl:grid-cols-2">
      <form onSubmit={save} className="space-y-6">
        <section className="card space-y-4">
          <div className="flex items-start justify-between gap-3">
            <div>
              <h2 className="text-xl font-bold">Challenge prompt</h2>
              <p className="text-sm text-mist">Shown to qualified teams together with the reference image.</p>
            </div>
            {locked ? <Badge tone="good">Published</Badge> : <Badge tone="warn">Draft</Badge>}
          </div>
          <div>
            <label className="label" htmlFor="title">Title</label>
            <input id="title" required maxLength={200} disabled={locked} className="field" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} />
          </div>
          <div>
            <label className="label" htmlFor="prompt">Participant-facing prompt</label>
            <textarea id="prompt" required rows={5} maxLength={4000} disabled={locked} className="field" value={form.prompt} onChange={(e) => setForm({ ...form, prompt: e.target.value })}
              placeholder="Recreate the provided reference website as accurately as possible. Match the layout, spacing, colors, typography, components, images, buttons, and responsive behavior." />
          </div>
          <div>
            <label className="label" htmlFor="requirements">Extra requirements (optional)</label>
            <textarea id="requirements" rows={3} maxLength={4000} disabled={locked} className="field" value={form.requirements} onChange={(e) => setForm({ ...form, requirements: e.target.value })} />
          </div>
          <div>
            <label className="label" htmlFor="limit">Time limit (minutes)</label>
            <input id="limit" type="number" min={1} max={600} required disabled={locked} className="field max-w-[10rem]" value={form.timeLimitMinutes} onChange={(e) => setForm({ ...form, timeLimitMinutes: Number(e.target.value) })} />
          </div>
        </section>

        <section className="card space-y-3 border-brass/40">
          <div>
            <h2 className="text-xl font-bold">Gemini evaluation prompt</h2>
            <p className="text-sm text-brass">Internal. Never sent to players. Gemini also receives the reference image and each team's code.</p>
          </div>
          <label className="sr-only" htmlFor="evalPrompt">Gemini evaluation prompt</label>
          <textarea id="evalPrompt" required rows={6} maxLength={6000} disabled={locked} className="field" value={form.evaluationPrompt} onChange={(e) => setForm({ ...form, evaluationPrompt: e.target.value })} />
          <p className="text-xs text-mist">The output format and the six scoring criteria are fixed by the server, so you cannot break the scoring by editing this text.</p>
        </section>

        {msg && (msg.ok ? <p role="status" className="rounded-lg border border-mint/40 bg-mint/10 px-4 py-3 text-sm text-mint">{msg.text}</p> : <ErrorNote>{msg.text}</ErrorNote>)}
        <div className="flex flex-wrap gap-3">
          <button className="btn-primary" disabled={busy || locked}>Save prompts</button>
          {!locked && <button type="button" className="btn-ghost" disabled={busy} onClick={() => { if (window.confirm("Publish Round 2? Qualified teams will see the reference image and prompt, and the challenge will be frozen.")) void run(async () => (await api.post<AdminChallenge>("/admin/round2/publish")).data, "Published."); }}>Publish challenge</button>}
          {locked && <button type="button" className="btn-danger" disabled={busy} onClick={() => void run(async () => (await api.post<AdminChallenge>("/admin/round2/unpublish")).data, "Unpublished.")}>Unpublish</button>}
        </div>
      </form>

      <section className="card h-fit space-y-4">
        <div>
          <h2 className="text-xl font-bold">Reference image</h2>
          <p className="text-sm text-mist">The website teams must recreate. PNG, JPEG or WEBP, up to 5 MB.</p>
        </div>
        {c.hasReferenceImage ? (
          image.url ? <img src={image.url} alt="Reference website" className="max-h-[32rem] w-full rounded-lg border border-edge object-contain object-top" /> : image.failed ? <ErrorNote>Preview failed to load.</ErrorNote> : <Spinner label="Loading preview" />
        ) : <Empty>No image uploaded yet.</Empty>}
        {c.referenceImageName && <p className="text-xs text-mist">{c.referenceImageName}</p>}
        {!locked && (
          <div>
            <label className="btn-ghost cursor-pointer">
              {c.hasReferenceImage ? "Replace image" : "Upload image"}
              <input ref={fileRef} type="file" accept="image/png,image/jpeg,image/webp" className="sr-only" disabled={busy} onChange={(e) => void upload(e.target.files?.[0])} />
            </label>
          </div>
        )}
      </section>
    </div>
  );
}

export default function AdminRound2() {
  const challenge = useLoad(async () => (await api.get<AdminChallenge>("/admin/round2/challenge")).data);
  const results = useLoad(async () => (await api.get<AdminResultRow[]>("/admin/round2/results")).data);
  const [detail, setDetail] = useState<AdminSubmissionDetail | null>(null);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [showCode, setShowCode] = useState(false);

  if (challenge.loading || results.loading) return <Spinner />;
  if (challenge.error || !challenge.data) return <ErrorNote>{challenge.error ?? "Could not load Round 2."}</ErrorNote>;
  if (results.error || !results.data) return <ErrorNote>{results.error ?? "Could not load results."}</ErrorNote>;

  async function open(id: number) {
    setBusy(true);
    setActionError(null);
    try {
      setDetail((await api.get<AdminSubmissionDetail>(`/admin/round2/submissions/${id}`)).data);
    } catch (e) {
      setActionError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  async function evaluate(id: number) {
    setBusy(true);
    setActionError(null);
    try {
      setDetail((await api.post<AdminSubmissionDetail>("/admin/round2/evaluate", { submissionId: id })).data);
      await results.reload();
    } catch (e) {
      setActionError(errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  return (
    <>
      <PageHeader title="Round 2" subtitle="Set up the challenge, then review submissions. Players never see prompts for evaluation, scores or feedback." />
      <ChallengeSetup key={challenge.data.id} initial={challenge.data} onChange={() => undefined} />

      <h2 className="mb-3 mt-10 text-2xl font-bold">Results</h2>
      <div className="grid gap-6 xl:grid-cols-[1fr_1.2fr]">
        <div>
          {results.data.length === 0 ? <Empty>No submissions yet.</Empty> : (
            <TableShell>
              <thead><tr>{["Rank", "Team", "Evaluation", "Score", "Submitted"].map((h) => <th key={h} className="th">{h}</th>)}</tr></thead>
              <tbody className="divide-y divide-edge">
                {results.data.map((r) => (
                  <tr key={r.submissionId} onClick={() => void open(r.submissionId)} className={`cursor-pointer hover:bg-raised/40 ${detail?.id === r.submissionId ? "bg-raised/60" : ""}`}>
                    <td className="td font-display text-lg font-bold tabular-nums">{r.finalScore === null ? "None" : r.rank}</td>
                    <td className="td font-semibold"><button className="text-left hover:text-brass" onClick={(e) => { e.stopPropagation(); void open(r.submissionId); }}>{r.teamName}</button></td>
                    <td className="td"><Badge tone={statusTone(r.evaluationStatus)}>{pretty(r.evaluationStatus)}</Badge></td>
                    <td className="td font-bold tabular-nums">{r.finalScore ?? "None"}</td>
                    <td className="td text-mist">{new Date(r.submittedAt).toLocaleString()}</td>
                  </tr>
                ))}
              </tbody>
            </TableShell>
          )}
        </div>

        <aside className="card h-fit">
          {!detail ? <p className="text-sm text-mist">{busy ? "Loading…" : "Select a submission to see the page, the AI evaluation and feedback."}</p> : (
            <div className="space-y-5">
              <div>
                <h3 className="text-xl font-bold">{detail.teamName}</h3>
                <p className="text-xs text-mist">Submitted {new Date(detail.submittedAt).toLocaleString()}</p>
              </div>
              {detail.notes && <p className="whitespace-pre-line rounded-lg bg-ink p-3 text-sm">{detail.notes}</p>}
              {detail.generatedHtml ? (
                <div>
                  <PreviewFrame html={detail.generatedHtml} height={420} />
                  <button className="mt-2 text-sm font-semibold text-brass hover:underline" onClick={() => setShowCode(!showCode)}>{showCode ? "Hide" : "Show"} generated code</button>
                  {showCode && <pre className="mt-2 max-h-72 overflow-auto rounded-lg bg-ink p-3 text-xs">{detail.generatedHtml}</pre>}
                </div>
              ) : <p className="text-sm text-mist">This submission has no generated page.</p>}
              <hr className="border-edge" />
              <EvaluationPanel ev={detail.evaluation} />
              {actionError && <ErrorNote>{actionError}</ErrorNote>}
              <button className="btn-primary w-full" disabled={busy} onClick={() => void evaluate(detail.id)}>
                {busy ? "Working…" : detail.evaluation ? (detail.evaluation.status === "FAILED" ? "Retry evaluation" : "Re-run evaluation") : "Run evaluation"}
              </button>
            </div>
          )}
        </aside>
      </div>
    </>
  );
}
