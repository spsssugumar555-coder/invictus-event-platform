import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { useAuth } from "../auth";
import { Brand } from "./ui";

const links = [
  { to: "/admin/dashboard", label: "Overview" },
  { to: "/admin/teams", label: "Teams" },
  { to: "/admin/questions", label: "Questions" },
  { to: "/admin/sequences", label: "Team sequences" },
  { to: "/admin/round1", label: "Round 1 live" },
  { to: "/admin/rankings", label: "Rankings" },
  { to: "/admin/round2", label: "Round 2" },
];

export default function AdminLayout() {
  const { session, logout } = useAuth();
  const navigate = useNavigate();
  return (
    <div className="min-h-screen md:flex">
      <aside className="border-b border-edge bg-panel md:sticky md:top-0 md:h-screen md:w-60 md:shrink-0 md:border-b-0 md:border-r">
        <div className="flex items-center justify-between gap-3 px-4 py-4 md:block">
          <Brand small />
          <p className="hidden pt-1 text-xs text-mist md:block">Admin console</p>
        </div>
        <nav className="flex gap-1 overflow-x-auto px-3 pb-3 md:flex-col md:pb-0" aria-label="Admin">
          {links.map((l) => (
            <NavLink key={l.to} to={l.to}
              className={({ isActive }) => `whitespace-nowrap rounded-lg px-3 py-2 text-sm font-medium transition ${isActive ? "bg-raised text-brass" : "text-mist hover:bg-raised/60 hover:text-paper"}`}>
              {l.label}
            </NavLink>
          ))}
        </nav>
        <div className="hidden px-4 pt-6 md:absolute md:bottom-0 md:block md:w-60 md:pb-5">
          <p className="mb-2 truncate text-xs text-mist">{session?.email}</p>
          <button className="btn-ghost w-full" onClick={() => { logout(); navigate("/admin/login"); }}>Log out</button>
        </div>
      </aside>
      <main className="min-w-0 flex-1 px-4 py-8 md:px-8">
        <div className="mb-4 flex justify-end md:hidden">
          <button className="btn-ghost !py-1.5" onClick={() => { logout(); navigate("/admin/login"); }}>Log out</button>
        </div>
        <Outlet />
      </main>
    </div>
  );
}
