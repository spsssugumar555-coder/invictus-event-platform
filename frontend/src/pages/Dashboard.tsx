import { Link } from "react-router-dom";
import { api } from "../api";
import { useLoad } from "../hooks";
import type { Dashboard as DashboardData } from "../types";
import { Badge, ErrorNote, PageHeader, Spinner, Stat, pretty, statusTone } from "../components/ui";

function Step({ n, title, state, detail }: { n: number; title: string; state: "done" | "active" | "idle" | "blocked"; detail: string }) {
  const ring = state === "done" ? "border-mint bg-mint text-ink" : state === "active" ? "border-brass bg-brass text-ink" : state === "blocked" ? "border-coral bg-coral/20 text-coral" : "border-edge bg-raised text-mist";
  return (
    <li className="flex gap-4">
      <span className={`mt-0.5 grid h-8 w-8 shrink-0 place-items-center rounded-full border-2 text-sm font-bold ${ring}`}>{n}</span>
      <div>
        <div className="font-semibold">{title}</div>
        <div className="text-sm text-mist">{detail}</div>
      </div>
    </li>
  );
}

export default function Dashboard() {
  const { data: d, error, loading } = useLoad(async () => (await api.get<DashboardData>("/player/dashboard")).data);

  if (loading) return <Spinner />;
  if (error || !d) return <ErrorNote>{error ?? "Could not load your dashboard."}</ErrorNote>;

  const r1 = d.round1;
  const progress = r1.total ? Math.round((r1.completed / r1.total) * 100) : 0;
  const q = d.qualificationStatus;
  const r1State = r1.status === "COMPLETED" || r1.status === "CLOSED" ? "done" : r1.status === "IN_PROGRESS" ? "active" : "idle";

  return (
    <>
      <PageHeader title={d.teamName} subtitle={`Captain: ${d.captainName}`}
        action={<Badge tone={statusTone(d.currentRound)}>{d.currentRound === "ENDED" ? "Competition ended for your team" : pretty(d.currentRound)}</Badge>} />

      <div className="grid gap-6 lg:grid-cols-3">
        <section className="card lg:col-span-2">
          <h2 className="mb-5 text-xl font-bold">Your path</h2>
          <ol className="space-y-6">
            <Step n={1} title="Round 1: Crossword" state={r1State}
              detail={r1.status === "NOT_PUBLISHED" ? "The organisers have not opened the crossword yet." : r1.status === "NOT_STARTED" ? `${r1.total} clues, 2 minutes each. Clues that time out loop back until every clue is solved.` : `${r1.completed} of ${r1.total} clues solved, ${r1.score} points.`} />
            <Step n={2} title="Qualification" state={q === "QUALIFIED" ? "done" : q === "NOT_QUALIFIED" ? "blocked" : "idle"}
              detail={q === "PENDING" ? "Only teams that solve every clue can qualify, and the top 10 advance. Organisers publish results after Round 1 closes."
                : q === "QUALIFIED" ? `You qualified${d.rank ? ` (rank ${d.rank})` : ""}.`
                : `Not qualified${d.rank ? ` (rank ${d.rank})` : ""}. Thanks for playing.`} />
            <Step n={3} title="Round 2: Build a website with AI"
              state={d.round2Status === "SUBMITTED" ? "done" : d.round2Status === "OPEN" ? "active" : d.round2Status === "NOT_QUALIFIED" ? "blocked" : "idle"}
              detail={d.round2Status === "OPEN" ? "Open now. Read the challenge and submit your links."
                : d.round2Status === "SUBMITTED" ? "Submitted. The judges will review it."
                : d.round2Status === "NOT_QUALIFIED" ? "Only the top 10 teams take part." : "Unlocks if you qualify."} />
          </ol>
          <div className="mt-7 flex flex-wrap gap-3">
            {r1.status === "COMPLETED" || r1.status === "CLOSED" || r1.status === "NOT_PUBLISHED" ? null : (
              <Link to="/round1" className="btn-primary">{r1.status === "NOT_STARTED" ? "Start Round 1" : "Continue Round 1"}</Link>
            )}
            {(d.round2Status === "OPEN" || d.round2Status === "SUBMITTED") && <Link to="/round2" className="btn-primary">Go to Round 2</Link>}
          </div>
        </section>

        <section className="card">
          <h2 className="mb-3 text-xl font-bold">Team</h2>
          <ul className="space-y-1.5 text-sm">
            <li className="font-medium">{d.captainName} <span className="text-mist">(captain)</span></li>
            {d.members.map((m) => <li key={m}>{m}</li>)}
          </ul>
          <p className="mt-4 break-all text-xs text-mist">{d.captainEmail}</p>
        </section>
      </div>

      <section className="mt-6">
        <h2 className="mb-3 text-xl font-bold">Round 1 progress</h2>
        <div className="mb-4 h-2.5 overflow-hidden rounded-full bg-raised" aria-label={`Round 1 progress ${progress}%`}>
          <div className="h-full rounded-full bg-brass transition-all" style={{ width: `${progress}%` }} />
        </div>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-5">
          <Stat label="Score" value={r1.score} />
          <Stat label="Correct" value={r1.correct} />
          <Stat label="Wrong answers" value={r1.incorrect} />
          <Stat label="Timed out" value={r1.timeouts} />
          <Stat label="Solved" value={`${r1.completed}/${r1.total}`} />
        </div>
      </section>
    </>
  );
}
