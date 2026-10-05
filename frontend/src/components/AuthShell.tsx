import type { ReactNode } from "react";
import { Brand } from "./ui";

const WORD = ["I", "N", "V", "I", "C", "T", "U", "S"];

export default function AuthShell({ title, subtitle, children, footer }: {
  title: string; subtitle: string; children: ReactNode; footer: ReactNode;
}) {
  return (
    <div className="grid min-h-screen lg:grid-cols-[1.05fr_1fr]">
      <aside className="relative hidden overflow-hidden border-r border-edge bg-panel p-12 lg:flex lg:flex-col lg:justify-between">
        <Brand />
        <div>
          <div className="mb-8 grid w-fit grid-cols-8 gap-1.5" aria-hidden="true">
            {WORD.map((ch, i) => (
              <span key={i} className="grid h-12 w-12 place-items-center rounded-md bg-paper font-display text-2xl font-extrabold text-ink">{ch}</span>
            ))}
          </div>
          <h2 className="max-w-md text-4xl font-bold leading-tight">Twenty clues. Two minutes each. Ten teams move on.</h2>
          <p className="mt-4 max-w-md text-mist">Solve the crossword round, qualify, then build a website with AI in the final challenge.</p>
        </div>
        <p className="text-sm text-mist">College technical event</p>
      </aside>
      <main className="flex items-center justify-center px-4 py-10">
        <div className="w-full max-w-md">
          <div className="mb-8 lg:hidden"><Brand /></div>
          <h1 className="text-3xl font-bold">{title}</h1>
          <p className="mt-1 mb-6 text-mist">{subtitle}</p>
          {children}
          <div className="mt-6 text-sm text-mist">{footer}</div>
        </div>
      </main>
    </div>
  );
}
