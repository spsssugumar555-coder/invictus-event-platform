import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../auth";
import { Brand } from "./ui";

const links = [
  { to: "/dashboard", label: "Dashboard" },
  { to: "/round1", label: "Round 1" },
  { to: "/round2", label: "Round 2" },
  { to: "/profile", label: "Profile" },
];

export default function PlayerLayout() {
  const { session, logout } = useAuth();
  const navigate = useNavigate();
  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-20 border-b border-edge bg-ink/90 backdrop-blur">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-x-6 gap-y-2 px-4 py-3">
          <Brand small />
          <nav className="order-3 flex w-full gap-1 overflow-x-auto sm:order-none sm:w-auto" aria-label="Main">
            {links.map((l) => (
              <NavLink key={l.to} to={l.to}
                className={({ isActive }) => `rounded-lg px-3 py-1.5 text-sm font-medium transition ${isActive ? "bg-raised text-brass" : "text-mist hover:text-paper"}`}>
                {l.label}
              </NavLink>
            ))}
          </nav>
          <div className="flex items-center gap-3">
            <span className="hidden max-w-[12rem] truncate text-sm text-mist sm:inline">{session?.teamName}</span>
            <button className="btn-ghost !py-1.5" onClick={() => { logout(); navigate("/login"); }}>Log out</button>
          </div>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-8"><Outlet /></main>
    </div>
  );
}
