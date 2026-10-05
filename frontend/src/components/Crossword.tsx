import type { CrosswordView } from "../types";

interface Highlight { row: number; col: number; direction: "ACROSS" | "DOWN"; length: number }

/**
 * Renders the complete crossword. Blocked cells are dark, open cells are paper-coloured with the clue
 * number in the corner. Letters only appear when the server sends them (words this team already solved,
 * or everything in the admin preview). The optional highlight marks the word the current clue refers to.
 */
export default function Crossword({ grid, highlight, compact = false }: {
  grid: CrosswordView; highlight?: Highlight | null; compact?: boolean;
}) {
  const marked = new Set<number>();
  if (highlight) {
    for (let i = 0; i < highlight.length; i++) {
      const r = highlight.row + (highlight.direction === "DOWN" ? i : 0);
      const c = highlight.col + (highlight.direction === "ACROSS" ? i : 0);
      marked.add(r * grid.cols + c);
    }
  }
  const cell = compact ? 26 : 40;
  return (
    <div className="overflow-x-auto rounded-xl border border-edge bg-panel p-3">
      <div role="img" aria-label="Crossword grid"
        className="mx-auto grid w-fit gap-px rounded-md bg-edge p-px"
        style={{ gridTemplateColumns: `repeat(${grid.cols}, ${cell}px)`, gridAutoRows: `${cell}px` }}>
        {grid.cells.map((c, i) => {
          if (!c.open) return <div key={i} className="bg-ink" />;
          const active = marked.has(i);
          return (
            <div key={i}
              className={`relative grid place-items-center font-display font-extrabold text-ink ${compact ? "text-xs" : "text-lg"} ${active ? "bg-brass ring-2 ring-inset ring-ink/60" : "bg-paper"}`}>
              {c.number !== null && <span className={`absolute left-0.5 top-0 font-bold leading-none text-ink/70 ${compact ? "text-[8px]" : "text-[10px]"}`}>{c.number}</span>}
              {c.letter}
            </div>
          );
        })}
      </div>
    </div>
  );
}
