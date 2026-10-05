import { api } from "../../api";
import { useLoad } from "../../hooks";
import type { AdminQualification, AdminRanking } from "../../types";
import { Badge, Empty, ErrorNote, PageHeader, Spinner, TableShell, pretty, statusTone } from "../../components/ui";

export default function AdminRankings() {
  const { data, error, loading } = useLoad(async () => {
    const [r, q] = await Promise.all([
      api.get<AdminRanking[]>("/admin/rankings"),
      api.get<AdminQualification[]>("/admin/qualifications"),
    ]);
    return { rankings: r.data, qualifications: q.data };
  });

  if (loading) return <Spinner />;
  if (error || !data) return <ErrorNote>{error ?? "Could not load rankings."}</ErrorNote>;

  const qualByTeam = new Map(data.qualifications.map((q) => [q.teamId, q.status]));
  const published = data.qualifications.length > 0;

  return (
    <>
      <PageHeader title="Rankings" subtitle={published ? "Qualification has been published." : "Qualification is not published yet. Only teams that solved every clue are eligible; the top 10 of those qualify."} />
      {data.rankings.length === 0 ? <Empty>No scores yet.</Empty> : (
        <TableShell>
          <thead><tr>{["Rank", "Team", "Score", "Correct", "Clues solved", "Qualification"].map((h) => <th key={h} className="th">{h}</th>)}</tr></thead>
          <tbody className="divide-y divide-edge">
            {data.rankings.map((r) => {
              const status = qualByTeam.get(r.teamId) ?? (r.status === "COMPLETED" ? "TOP_10_SO_FAR" : "PENDING");
              return (
                <tr key={r.teamId} className={r.status === "COMPLETED" && r.rank <= 10 ? "bg-brass/5" : ""}>
                  <td className="td font-display text-lg font-bold tabular-nums">{r.rank}</td>
                  <td className="td font-semibold">{r.teamName}</td>
                  <td className="td font-bold tabular-nums">{r.score}</td>
                  <td className="td tabular-nums">{r.correct}</td>
                  <td className="td tabular-nums">{r.completed}</td>
                  <td className="td"><Badge tone={status === "TOP_10_SO_FAR" ? "info" : statusTone(status)}>{status === "TOP_10_SO_FAR" ? "Completed (eligible)" : pretty(status)}</Badge></td>
                </tr>
              );
            })}
          </tbody>
        </TableShell>
      )}
    </>
  );
}
