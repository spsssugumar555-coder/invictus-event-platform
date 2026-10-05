import { Navigate, Route, Routes } from "react-router-dom";
import { RequireRole, useAuth } from "./auth";
import PlayerLayout from "./components/PlayerLayout";
import AdminLayout from "./components/AdminLayout";
import Login from "./pages/Login";
import Register from "./pages/Register";
import Dashboard from "./pages/Dashboard";
import Round1 from "./pages/Round1";
import Round2 from "./pages/Round2";
import Profile from "./pages/Profile";
import AdminLogin from "./pages/admin/AdminLogin";
import AdminDashboard from "./pages/admin/AdminDashboard";
import AdminTeams from "./pages/admin/AdminTeams";
import AdminRound1 from "./pages/admin/AdminRound1";
import AdminQuestions from "./pages/admin/AdminQuestions";
import AdminSequences from "./pages/admin/AdminSequences";
import AdminRankings from "./pages/admin/AdminRankings";
import AdminRound2 from "./pages/admin/AdminRound2";

function Home() {
  const { session } = useAuth();
  if (!session) return <Navigate to="/login" replace />;
  return <Navigate to={session.role === "ADMIN" ? "/admin/dashboard" : "/dashboard"} replace />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route element={<RequireRole role="PLAYER"><PlayerLayout /></RequireRole>}>
        <Route path="/dashboard" element={<Dashboard />} />
        <Route path="/round1" element={<Round1 />} />
        <Route path="/round2" element={<Round2 />} />
        <Route path="/profile" element={<Profile />} />
      </Route>
      <Route path="/admin/login" element={<AdminLogin />} />
      <Route path="/admin" element={<Navigate to="/admin/dashboard" replace />} />
      <Route element={<RequireRole role="ADMIN"><AdminLayout /></RequireRole>}>
        <Route path="/admin/dashboard" element={<AdminDashboard />} />
        <Route path="/admin/teams" element={<AdminTeams />} />
        <Route path="/admin/questions" element={<AdminQuestions />} />
        <Route path="/admin/sequences" element={<AdminSequences />} />
        <Route path="/admin/round1" element={<AdminRound1 />} />
        <Route path="/admin/rankings" element={<AdminRankings />} />
        <Route path="/admin/round2" element={<AdminRound2 />} />
      </Route>
      <Route path="*" element={<Home />} />
    </Routes>
  );
}
