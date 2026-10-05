import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { api, errorMessage, statusOf } from "../api";
import type { Round1State } from "../types";
import Crossword from "../components/Crossword";
import { ErrorNote, Spinner } from "../components/ui";

type Flash = { tone: "good" | "bad"; text: string };

function flashFor(result: string | null): Flash | null {
  switch (result) {
    case "CORRECT": return { tone: "good", text: "Correct! That clue is solved for good." };
    case "INCORRECT": return { tone: "bad", text: "Wrong answer. Try again." };
    case "TIMEOUT": return { tone: "bad", text: "Time's up! That clue goes to the back of your queue." };
    default: return null;
  }
}
const flashStyle = { good: "border-mint/50 bg-mint/10 text-mint", bad: "border-coral/50 bg-coral/10 text-coral" };

function fmt(ms: number) {
  const s = Math.ceil(ms / 1000);
  return `${String(Math.floor(s / 60)).padStart(2, "0")}:${String(s % 60).padStart(2, "0")}`;
}

export default function Round1() {
  const [state, setState] = useState<Round1State | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [flash, setFlash] = useState<Flash | null>(null);
  const [value, setValue] = useState("");
  const [busy, setBusy] = useState(false);
  const [deadline, setDeadline] = useState(0);
  const [now, setNow] = useState(() => performance.now());
  const inputRef = useRef<HTMLInputElement>(null);
  const expiredFor = useRef<number | null>(null);
  const flashTimer = useRef<number | undefined>(undefined);

  // The server owns the clock and the queue. We only display what it reports.
  const apply = useCallback((next: Round1State, announce: boolean) => {
    setState(next);
    setValue("");
    if (next.question) setDeadline(performance.now() + next.question.remainingMs);
    if (announce) {
      setFlash(flashFor(next.lastResult));
      window.clearTimeout(flashTimer.current);
      flashTimer.current = window.setTimeout(() => setFlash(null), 3500);
    }
    window.setTimeout(() => inputRef.current?.focus(), 0);
  }, []);

  const refresh = useCallback(async (announce: boolean) => {
    try {
      const { data } = await api.get<Round1State>("/round1/current");
      apply(data, announce);
      setError(null);
      return data;
    } catch (e) {
      setError(errorMessage(e));
      return null;
    }
  }, [apply]);

  useEffect(() => { void refresh(false); }, [refresh]);
  useEffect(() => () => window.clearTimeout(flashTimer.current), []);

  useEffect(() => {
    const id = window.setInterval(() => setNow(performance.now()), 200);
    return () => window.clearInterval(id);
  }, []);

  // Before the organisers publish, check again every 10 seconds.
  const status = state?.summary.status;
  useEffect(() => {
    if (status !== "NOT_PUBLISHED") return;
    const id = window.setInterval(() => void refresh(false), 10_000);
    return () => window.clearInterval(id);
  }, [status, refresh]);

  const q = state?.question ?? null;
  const remaining = q ? Math.max(0, deadline - now) : 0;

  // When the countdown reaches zero, ask the server what happened. It settles the timeout and sends the next clue.
  useEffect(() => {
    if (!q || remaining > 0 || busy || expiredFor.current === q.attemptId) return;
    expiredFor.current = q.attemptId;
    void refresh(true).then((data) => {
      if (data?.question?.attemptId === q.attemptId) expiredFor.current = null; // server clock not there yet, retry
    });
  }, [q, remaining, busy, refresh]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!q || !value.trim() || busy) return;
    setBusy(true);
    try {
      const { data } = await api.post<Round1State>("/round1/answer", { attemptId: q.attemptId, answer: value });
      apply(data, true);
      setError(null);
    } catch (err) {
      if (statusOf(err) === 409 || statusOf(err) === 404) {
        await refresh(false); // our view was stale: show whatever the server says is current
        setError("That clue had already changed. Showing your current clue.");
      } else {
        setError(errorMessage(err));
      }
    } finally {
      setBusy(false);
    }
  }

  if (!state && !error) return <Spinner label="Loading your crossword" />;
  if (!state) return <ErrorNote>{error}</ErrorNote>;

  const s = state.summary;

  if (s.status === "NOT_PUBLISHED") {
    return <EndCard title="Round 1 hasn't opened yet" body="The organisers have not published the crossword yet. This page checks every 10 seconds and will start your round as soon as it opens." />;
  }
  if (s.status === "CLOSED" && !q) {
    return <EndCard title="Round 1 has closed" body={`You solved ${s.completed} of ${s.total} clues. Check your dashboard for the qualification result.`} />;
  }
  if (s.status === "COMPLETED" || !q) {
    return (
      <div className="mx-auto max-w-4xl space-y-6">
        <EndCard title="Crossword complete!" body={`All ${s.total} clues solved: ${s.score} points, ${s.incorrect} wrong answers, ${s.timeouts} time-outs. Qualification is announced by the organisers.`} />
        {state.crossword && <Crossword grid={state.crossword} />}
      </div>
    );
  }

  const pct = Math.min(100, (remaining / q.totalMs) * 100);
  const urgent = remaining <= 20_000;

  return (
    <div className="mx-auto max-w-4xl">
      <div className="mb-4 flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold">Round 1: Crossword</h1>
          <p className="text-sm text-mist">Solved {s.completed} of {s.total} · Score {s.score} · Wrong {s.incorrect} · Time-outs {s.timeouts}</p>
        </div>
        <div className={`font-display text-5xl font-extrabold tabular-nums ${urgent ? "text-coral" : "text-brass"}`} role="timer" aria-live="off">
          {fmt(remaining)}
        </div>
      </div>

      <div className="mb-5 h-2 overflow-hidden rounded-full bg-raised">
        <div className={`h-full rounded-full ${urgent ? "bg-coral" : "bg-brass"}`} style={{ width: `${pct}%`, transition: "width 200ms linear" }} />
      </div>

      <div className="mb-5 flex gap-1" aria-label={`${s.completed} of ${s.total} clues solved`}>
        {Array.from({ length: s.total }, (_, i) => (
          <span key={i} className={`h-2 flex-1 rounded-sm ${i < s.completed ? "bg-mint" : "bg-raised"}`} />
        ))}
      </div>

      {state.crossword && <Crossword grid={state.crossword} highlight={{ row: q.row, col: q.col, direction: q.direction, length: q.length }} />}

      <div className="mt-5 min-h-[3rem]" aria-live="polite">
        {flash && <div role="status" className={`rounded-lg border px-4 py-3 text-sm font-medium animate-pop ${flashStyle[flash.tone]}`}>{flash.text}</div>}
        {error && <ErrorNote>{error}</ErrorNote>}
      </div>

      <section className="card">
        <div className="mb-1 text-sm font-semibold text-brass">{q.displayNumber} {q.direction === "DOWN" ? "Down" : "Across"} · {q.length} letters</div>
        <p className="font-display text-2xl font-semibold leading-snug sm:text-3xl">{q.clue}</p>

        <form onSubmit={submit} className="mt-6 flex flex-col gap-3 sm:flex-row">
          <label htmlFor="answer" className="sr-only">Your answer</label>
          <input id="answer" ref={inputRef} autoFocus autoComplete="off" autoCapitalize="characters" spellCheck={false}
            maxLength={Math.max(q.length, 1) + 5} value={value} disabled={busy} placeholder={`${q.length} letters`}
            onChange={(e) => setValue(e.target.value.toUpperCase())}
            className="field flex-1 font-display text-2xl font-bold tracking-[0.25em] uppercase" />
          <button className="btn-primary sm:min-w-[10rem]" disabled={busy || value.trim().length === 0}>{busy ? "Checking…" : "Submit answer"}</button>
        </form>
        <p className="mt-3 text-xs text-mist">You can try again until the timer runs out. If time runs out, the clue goes to the back of your queue. You finish when every clue is solved.</p>
      </section>
    </div>
  );
}

function EndCard({ title, body }: { title: string; body: string }) {
  return (
    <div className="mx-auto max-w-xl card text-center">
      <h1 className="text-3xl font-bold">{title}</h1>
      <p className="mt-3 text-mist">{body}</p>
      <Link to="/dashboard" className="btn-primary mt-6">Back to dashboard</Link>
    </div>
  );
}
