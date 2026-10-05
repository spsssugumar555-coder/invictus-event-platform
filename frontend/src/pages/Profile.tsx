import { useNavigate } from "react-router-dom";
import { api } from "../api";
import { useAuth } from "../auth";
import { useLoad } from "../hooks";
import type { Dashboard } from "../types";
import { ErrorNote, PageHeader, Spinner } from "../components/ui";

export default function Profile() {
  const { session, logout } = useAuth();
  const navigate = useNavigate();
  const { data: d, error, loading } = useLoad(async () => (await api.get<Dashboard>("/player/dashboard")).data);

  if (loading) return <Spinner />;
  if (error || !d) return <ErrorNote>{error ?? "Could not load your profile."}</ErrorNote>;

  return (
    <>
      <PageHeader title="Profile" subtitle="Your team details. Contact the organisers if something needs correcting." />
      <section className="card max-w-2xl">
        <dl className="grid gap-4 sm:grid-cols-2">
          <div><dt className="text-sm text-mist">Team name</dt><dd className="font-semibold">{d.teamName}</dd></div>
          <div><dt className="text-sm text-mist">Captain</dt><dd className="font-semibold">{d.captainName}</dd></div>
          <div className="sm:col-span-2"><dt className="text-sm text-mist">Sign-in email</dt><dd className="break-all font-semibold">{session?.email}</dd></div>
          <div className="sm:col-span-2">
            <dt className="text-sm text-mist">Members</dt>
            <dd>{d.members.length ? d.members.join(", ") : "Captain only"}</dd>
          </div>
        </dl>
        <button className="btn-ghost mt-6" onClick={() => { logout(); navigate("/login"); }}>Log out</button>
      </section>
    </>
  );
}
