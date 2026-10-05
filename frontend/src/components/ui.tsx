import type { ReactNode } from "react";

export function Brand({ small = false }: { small?: boolean }) {
  return (
    <span className="inline-flex items-center gap-2.5">
      <span className={`grid place-items-center rounded-md bg-paper font-display font-extrabold text-ink ${small ? "h-7 w-7 text-base" : "h-9 w-9 text-xl"}`}>I</span>
      <span className={`font-display font-bold tracking-tight ${small ? "text-lg" : "text-2xl"}`}>Invictus</span>
    </span>
  );
}

export function Spinner({ label = "Loading" }: { label?: string }) {
  return (
    <div role="status" className="flex items-center gap-3 py-10 text-mist">
      <span className="h-5 w-5 animate-spin rounded-full border-2 border-edge border-t-brass" />
      <span>{label}…</span>
    </div>
  );
}

export function ErrorNote({ children }: { children: ReactNode }) {
  return <div role="alert" className="rounded-lg border border-coral/50 bg-coral/10 px-4 py-3 text-sm text-coral">{children}</div>;
}

type Tone = "neutral" | "good" | "bad" | "warn" | "info";
const tones: Record<Tone, string> = {
  neutral: "bg-raised text-mist border-edge",
  good: "bg-mint/15 text-mint border-mint/40",
  bad: "bg-coral/15 text-coral border-coral/40",
  warn: "bg-brass/15 text-brass border-brass/40",
  info: "bg-paper/10 text-paper border-paper/30",
};
export function Badge({ tone = "neutral", children }: { tone?: Tone; children: ReactNode }) {
  return <span className={`inline-block whitespace-nowrap rounded-full border px-2.5 py-0.5 text-xs font-semibold ${tones[tone]}`}>{children}</span>;
}

export function statusTone(s: string): Tone {
  if (["QUALIFIED", "COMPLETED", "SUBMITTED", "OPEN"].includes(s)) return "good";
  if (["NOT_QUALIFIED", "FAILED", "CLOSED", "ENDED"].includes(s)) return "bad";
  if (["PENDING", "IN_PROGRESS", "ROUND_1", "ROUND_2"].includes(s)) return "warn";
  return "neutral";
}
export function pretty(s: string): string {
  const t = s.replace(/_/g, " ").toLowerCase();
  return t.charAt(0).toUpperCase() + t.slice(1);
}

export function Stat({ label, value, hint }: { label: string; value: ReactNode; hint?: string }) {
  return (
    <div className="rounded-xl border border-edge bg-panel p-4">
      <div className="text-sm text-mist">{label}</div>
      <div className="mt-1 font-display text-3xl font-bold">{value}</div>
      {hint && <div className="mt-1 text-xs text-mist">{hint}</div>}
    </div>
  );
}

export function PageHeader({ title, subtitle, action }: { title: string; subtitle?: string; action?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-3">
      <div>
        <h1 className="text-3xl font-bold">{title}</h1>
        {subtitle && <p className="mt-1 max-w-2xl text-mist">{subtitle}</p>}
      </div>
      {action}
    </div>
  );
}

export function TableShell({ children }: { children: ReactNode }) {
  return (
    <div className="overflow-x-auto rounded-2xl border border-edge bg-panel">
      <table className="min-w-full divide-y divide-edge">{children}</table>
    </div>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="rounded-2xl border border-dashed border-edge px-6 py-10 text-center text-mist">{children}</div>;
}
