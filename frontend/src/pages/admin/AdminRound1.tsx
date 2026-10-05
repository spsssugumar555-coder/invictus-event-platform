import { useCallback, useEffect, useState } from "react";
import { api, errorMessage } from "../../api";
import type { AdminRound1Row } from "../../types";
import { Badge, Empty, ErrorNote, PageHeader, Spinner, TableShell, pretty, statusTone } from "../../components/ui";

export default function AdminRound1() {
  const [rows, setRows] = useState<AdminRound1Row[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setRows((await api.get<AdminRound1Row[]>("/admin/round1")).data);
      setError(null);
    } catch (e) {
      setError(errorMessage(e));
    }
  }, []);

  useEffect(() => {
    void load();
    const id = window.setInterval(() => void load(), 10_000); // live view
    return () => window.clearInterval(id);
  }, [load]);

  if (!rows && !error) return <Spinner />;
  if (!rows) return <ErrorNote>{error}</ErrorNote>;

  return (
    <>
      <PageHeader title="Round 1 live" subtitle="Refreshes every 10 seconds. Teams finish only when every question is answered correctly." />
      {error && <div className="mb-4"><ErrorNote>{error}</ErrorNote></div>}
      {rows.length === 0 ? <Empty>No team has started Round 1 yet.</Empty> : (
        <TableShell>
          <thead><tr>{["Rank", "Team", "Status", "Solved", "Current question", "Correct", "Incorrect", "Timeouts", "Score"].map((h) => <th key={h} className="th">{h}</th>)}</tr></thead>
          <tbody className="divide-y divide-edge">
            {rows.map((r) => (
              <tr key={r.teamId} className="hover:bg-raised/40">
                <td className="td tabular-nums">{r.rank}</td>
                <td className="td font-semibold">{r.teamName}</td>
                <td className="td"><Badge tone={statusTone(r.status)}>{pretty(r.status)}</Badge></td>
                <td className="td tabular-nums">{r.completed}/{r.total}</td>
                <td className="td tabular-nums">{r.currentQuestionNumber ? `Q${r.currentQuestionNumber}` : "None"}</td>
                <td className="td tabular-nums text-mint">{r.correct}</td>
                <td className="td tabular-nums text-coral">{r.incorrect}</td>
                <td className="td tabular-nums">{r.timeouts}</td>
                <td className="td font-bold tabular-nums">{r.score}</td>
              </tr>
            ))}
          </tbody>
        </TableShell>
      )}
    </>
  );
}
