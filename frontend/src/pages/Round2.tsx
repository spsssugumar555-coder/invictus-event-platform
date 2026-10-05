import { useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { api, errorMessage } from "../api";
import { useBlobUrl, useLoad } from "../hooks";
import type { GenerateResponse, Round2View } from "../types";
import PreviewFrame from "../components/PreviewFrame";
import { Badge, ErrorNote, PageHeader, Spinner } from "../components/ui";

export default function Round2() {
  const { data, error, status, loading, reload } = useLoad(async () => (await api.get<Round2View>("/round2")).data);
  const [html, setHtml] = useState<string | null>(null);
  const [left, setLeft] = useState(0);
  const [instruction, setInstruction] = useState("");
  const [notes, setNotes] = useState("");
  const [generating, setGenerating] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [msg, setMsg] = useState<string | null>(null);
  const image = useBlobUrl(data ? "/round2/reference-image" : null);

  useEffect(() => {
    if (data) {
      setHtml(data.draftHtml);
      setLeft(data.generationsLeft);
    }
  }, [data]);

  if (loading) return <Spinner />;

  if (status === 403) {
    return (
      <div className="mx-auto max-w-xl card text-center">
        <h1 className="text-3xl font-bold">Round 2 is locked</h1>
        <p className="mt-3 text-mist">Round 2 is open to the top 10 teams from Round 1. Your dashboard shows your qualification status once the organisers publish it.</p>
        <Link to="/dashboard" className="btn-primary mt-6">Back to dashboard</Link>
      </div>
    );
  }
  if (status === 409) {
    return (
      <div className="mx-auto max-w-xl card text-center">
        <h1 className="text-3xl font-bold">Round 2 is almost ready</h1>
        <p className="mt-3 text-mist">You qualified. The organisers have not published the challenge yet. Check back in a moment.</p>
        <button className="btn-primary mt-6" onClick={() => void reload()}>Check again</button>
      </div>
    );
  }
  if (error || !data) return <ErrorNote>{error ?? "Could not load Round 2."}</ErrorNote>;

  const { challenge: c, submission: sub } = data;

  if (sub) {
    return (
      <div className="mx-auto max-w-xl card text-center">
        <h1 className="text-3xl font-bold">Submission received successfully</h1>
        <p className="mt-3 text-mist">Your Round 2 result will be announced by the organizers.</p>
        <p className="mt-2 text-xs text-mist">Submitted {new Date(sub.submittedAt).toLocaleString()}</p>
        <Link to="/dashboard" className="btn-primary mt-6">Back to dashboard</Link>
      </div>
    );
  }

  async function generate(e: FormEvent) {
    e.preventDefault();
    setGenerating(true);
    setMsg(null);
    try {
      const { data: r } = await api.post<GenerateResponse>("/round2/generate", { instruction });
      setHtml(r.html);
      setLeft(r.generationsLeft);
      setInstruction("");
    } catch (err) {
      setMsg(errorMessage(err));
    } finally {
      setGenerating(false);
    }
  }

  async function submit() {
    if (!window.confirm("Submit this website? You can only submit once and cannot change it afterwards.")) return;
    setSubmitting(true);
    setMsg(null);
    try {
      await api.post("/round2/submit", { notes });
      await reload();
    } catch (err) {
      setMsg(errorMessage(err));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <>
      <PageHeader title={c.title} subtitle={c.prompt} action={<Badge tone="warn">{c.timeLimitMinutes} minute limit</Badge>} />
      <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_minmax(0,1.2fr)]">
        <div className="space-y-6">
          <section className="card">
            <h2 className="mb-3 text-xl font-bold">Reference design</h2>
            {image.url ? (
              <a href={image.url} target="_blank" rel="noopener noreferrer" title="Open full size">
                <img src={image.url} alt="Reference website to recreate" className="max-h-[34rem] w-full rounded-lg border border-edge object-contain object-top" />
              </a>
            ) : image.failed ? <ErrorNote>The reference image could not be loaded.</ErrorNote> : <Spinner label="Loading image" />}
          </section>
          {c.requirements && (
            <section className="card">
              <h2 className="mb-2 text-xl font-bold">Requirements</h2>
              <p className="whitespace-pre-line leading-relaxed text-paper/90">{c.requirements}</p>
            </section>
          )}
        </div>

        <div className="space-y-6">
          <section className="card">
            <div className="mb-3 flex items-center justify-between gap-3">
              <h2 className="text-xl font-bold">AI website builder</h2>
              <span className="text-sm text-mist">{left} generations left</span>
            </div>
            <form onSubmit={generate} className="space-y-3">
              <label className="label" htmlFor="instruction">{html ? "What should change?" : "Anything extra for the AI? (optional)"}</label>
              <textarea id="instruction" rows={3} maxLength={2000} className="field" value={instruction} onChange={(e) => setInstruction(e.target.value)}
                placeholder={html ? "e.g. Make the hero section taller and the buttons rounder" : "e.g. Use a dark navigation bar"} />
              <button className="btn-primary w-full" disabled={generating || left <= 0}>
                {generating ? "Generating… this can take up to a minute" : html ? "Refine with AI" : "Generate from reference"}
              </button>
            </form>
            {msg && <div className="mt-3"><ErrorNote>{msg}</ErrorNote></div>}
          </section>

          <section className="card">
            <h2 className="mb-3 text-xl font-bold">Live preview</h2>
            {html ? <PreviewFrame html={html} /> : <p className="py-10 text-center text-mist">Your generated website will appear here.</p>}
          </section>

          {html && (
            <section className="card">
              <h2 className="mb-2 text-xl font-bold">Submit</h2>
              <p className="mb-3 text-sm text-mist">The judges receive the latest generated version shown above.</p>
              <label className="label" htmlFor="notes">Note for the judges (optional)</label>
              <textarea id="notes" rows={2} maxLength={2000} className="field" value={notes} onChange={(e) => setNotes(e.target.value)} />
              <button className="btn-primary mt-3 w-full" disabled={submitting || generating} onClick={() => void submit()}>{submitting ? "Submitting…" : "Submit final website"}</button>
            </section>
          )}
        </div>
      </div>
    </>
  );
}
