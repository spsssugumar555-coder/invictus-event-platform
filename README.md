# Invictus Event Platform

A two-round college tech competition platform.

- **Round 1:** crossword. 20 clues, 2 minutes each, assigned by the server.
- **Round 2:** top 10 teams build a website with AI and submit links. The backend asks Gemini to judge it.
- **Roles:** `PLAYER` (a team) and `ADMIN` (organiser). Roles are enforced by Spring Security, not by the UI.

## Architecture

```
React + Vite + Tailwind (Vercel)  --Axios/JWT-->  Spring Boot 3.3 (Render)  -->  PostgreSQL (Neon)
                                                        |
                                                        +--> Gemini API (server-side only)
```

Backend package `com.invictus.platform`: `config`, `controller`, `dto`, `entity`, `repository`, `service`, `security`, `exception`, `ai`.

### Round 1: crossword (admin-built, server-driven)

- **Questions are admin-created** (`/admin/questions`): clue + answer, add / edit / delete / reorder. The 20 questions from the original seed are only samples; delete or edit them. Round 1 stays closed to teams until the admin clicks **Publish round 1**.
- **Crossword generation.** On publish, `CrosswordGenerator` lays out all answers (deterministic, no randomness) and the positions, directions and clue numbers are stored on the questions, so every team sees the same grid. If an answer cannot be fitted (it shares no letters with the rest) publishing is refused and the offending answers are named. The published set is frozen; it can be unpublished only before any team has started.
- **Players see the whole grid** and the current clue (highlighted in the grid). Letters appear only for clues that team has already solved. The API has no field for answers or the solution.
- **Queue per team.** Each team has a server-side queue. Correct = question removed for good (+10). Wrong answer = the same question stays active on the same deadline and can be retried. Timeout = question moves to the end. The next question starts a fresh 120 second server timer. **There is no skip and no manual "next" endpoint.**
- **Sequences.** Admin sets each team's first questions by question number (`/admin/sequences`), or uses "Auto-assign staggered". Anything not listed follows in a deterministic team-specific order. Sequences lock once a team starts.
- **Completion.** Round 1 is `COMPLETED` only when every question has been answered correctly.
- **Stale attempts.** Answering an attempt that is closed, timed out, or answered returns `409` and changes nothing; an attempt from another team returns `404`.
- **Timer.** `expiresAt` is stored server-side. The browser only counts down from the `remainingMs` the server reports and asks the server what happened at zero. Timeouts are settled on the next request from that team (no background job).
- **Qualification.** `POST /api/admin/qualifications/finalize` (button on the admin overview) ranks only teams that COMPLETED Round 1, using the existing order (score, then correct answers, then earliest completion), and the top 10 become QUALIFIED. Everyone else is NOT_QUALIFIED and Round 1 locks.

### Round 2: website recreation with Gemini

- **Admin setup** (`/admin/round2`): upload a reference image (PNG/JPEG/WEBP, 5 MB, type checked by file signature, stored in PostgreSQL so Render's ephemeral disk cannot lose it), write the **participant prompt** and the separate **Gemini evaluation prompt** (internal), then publish.
- **Qualified players** see the reference image and participant prompt only. The AI builder (`POST /api/round2/generate`) runs on the server with the key in `GEMINI_API_KEY`; the browser never sees the key or the evaluation prompt. The latest generated page is stored server-side as the team's draft, can be refined (`instruction`), and is shown in a sandboxed iframe (`sandbox="allow-scripts"`, no same-origin access). Each team has a generation cap (`ROUND2_MAX_GENERATIONS`, default 40).
- **Submit** stores the server-held draft (the browser cannot send different code), then Gemini evaluates it using the reference image, the participant prompt, the internal evaluation prompt and the generated HTML. Players receive only a confirmation.
- **Admin only:** evaluation prompt, scores, six criteria, feedback, raw Gemini response, ranking (`/api/admin/round2/**`, `ROLE_ADMIN`). Player DTOs contain none of these fields.
- Gemini failures are stored as `FAILED`; the admin can retry.
- Final Round 2 score (0-100) is computed by the backend from six 0-10 criteria: requirements 25, functionality 20, visual match 15, responsiveness 10, code quality 15, overall fidelity 15.

### Admin team management

`/admin/teams` lists every team with its Round 1, qualification and Round 2 status, and lets the organizer **add**, **edit** and **delete** teams (`POST/PUT/DELETE /api/admin/teams`, `ROLE_ADMIN` only; participants get 403).

- **Create** applies the same rules as self-registration (unique team name and email, 8+ character password) and creates a login for the captain. It creates no Round 1 session and no qualification; use Team sequences to configure the starting questions.
- **Edit** changes name, captain, email, members and (optionally) the password. It never touches the Round 1 session, queue, sequence, qualification or any Round 2 data, so a running timer is not reset.
- **Delete** removes, in one transaction and in foreign-key order: evaluations, submissions, drafts, attempts, queue items, session, sequence items, qualification, members, the team, and its login. Other teams and the question set are untouched. If Round 1 was already closed, deleting a qualified team does not promote anyone else; re-run the decision yourself if you need to.

### Team management (admin)

`/admin/teams` lets the organiser add, edit and delete teams (`GET/POST /api/admin/teams`, `PUT/DELETE /api/admin/teams/{id}`, ROLE_ADMIN only).

- **Add** uses the same rules as self-registration (name/email uniqueness, password rules) and creates no Round 1 or Round 2 data; the new team is `PENDING` and gets its sequence from the normal sequence tools.
- **Edit** changes identity fields only (name, captain, email, members, optional new password). Round 1 progress, sequence, qualification and Round 2 data are never touched.
- **Delete** removes the team and everything that references it in dependency order, in one transaction: evaluations, submissions, drafts, attempts, queue items, session, sequence, qualification, members, team, login account. Other teams and the question set are untouched. The UI requires typing the team name to confirm. Deleting after Round 1 was closed removes that team's qualification row, so counts drop and no one is promoted automatically.

## Environment variables

See `.env.example`. Required: `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `JWT_SECRET` (32+ chars), `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `GEMINI_API_KEY`, `FRONTEND_URL`. Optional: `PUBLIC_BASE_URL`, `PORT`, `ROUND2_MAX_GENERATIONS` (default 40), `GEMINI_MAX_OUTPUT_TOKENS` (leave unset so the model uses its own limit). `GEMINI_MODEL` has no default and must be set. Frontend: `VITE_API_URL`.

## Run locally

Prerequisites: Java 21, Maven 3.9+, Node 20+, Docker (for Postgres).

```bash
# 1. Database
docker compose up -d

# 2. Backend (new terminal)
cd backend
export JWT_SECRET="$(openssl rand -base64 48)"
export ADMIN_EMAIL=admin@example.com
export ADMIN_PASSWORD='choose-a-strong-password'
export GEMINI_API_KEY=your-key          # optional until you test Round 2 evaluation
mvn spring-boot:run

# 3. Frontend (new terminal)
cd frontend
npm install
npm run dev                              # http://localhost:5173 (proxies /api to :8080)
```

Flyway creates the schema and seeds the 20 questions and the Round 2 challenge on first start (`ddl-auto=validate`).

### Try it end to end

1. Sign in at `/admin/login`. In **Questions**, replace the sample questions with your own, click **Generate preview**, then **Publish round 1**.
2. In **Team sequences**, set each team's starting questions (or Auto-assign), before teams start.
3. Register teams at `/register` and play `/round1`. Wrong answers and time-outs send a clue to the back of the queue; the round completes only when every clue is solved.
4. On the admin overview, **Close Round 1** to publish the top 10 of the teams that completed it.
5. In **Round 2**, upload the reference image, write both prompts, publish. A qualified team opens `/round2`, generates, refines, previews and submits. Results appear on the admin Round 2 page only.

Existing test data: sessions that were completed under the old "all attempted" rule are re-opened by migration `V3`. If you want a clean slate for the event, clear Round 1 test rows yourself, in this order: `round1_attempts`, `round1_queue_items`, `round1_sessions`, `qualifications`.

## Tests

```bash
cd backend && mvn clean test      # H2 in PostgreSQL mode, Gemini mocked
cd frontend && npm run build      # type-check + production build
```

Backend tests (`PlatformFlowTest`, `Round1QueueTest`, `QualificationAndRound2Test`) cover: auth and role boundaries, admin question CRUD / reorder / crossword publish, unplaceable answers, per-team sequences, queue behaviour (wrong and timeout go to the end, correct is removed, completion only when all are correct), stale and foreign attempts, server timer consistency (run under Asia/Kolkata), no answers in player responses, top-10 qualification among completers, Round 2 setup, reference image access, Gemini builder (server-side, internal prompt never sent to players), submission, evaluation, retry, and that player responses contain no evaluation data.

## Gemini setup

Create a key in Google AI Studio and set `GEMINI_API_KEY` on the backend only. Set `GEMINI_MODEL` to the exact model id you want (required; there is no default in the code, and `models/<id>` is also accepted). A 404 from Google means the id is wrong for your key and is reported to players as a configuration error. Temporary 503 and 429 responses are retried up to 3 times (waits of 2s, 5s, 10s); 401, 403, 404 and 400 are never retried. Optionally set `GEMINI_MAX_OUTPUT_TOKENS` to cap output; by default nothing is sent and the model uses its own limit (a fixed cap can be rejected by some models or used up by a thinking model before any page text is produced). The server log shows the failure kind, HTTP status, `finishReason` and token counts for each failed call, never the key or any prompt text. Never use a `VITE_` variable for it.

## Neon setup

1. Create a project and database in Neon.
2. Copy the host, user and password. Build the JDBC URL: `jdbc:postgresql://<host>/<db>?sslmode=require`.
3. Set `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD` on the backend.

## Deploy backend to Render

1. New Web Service from your repo, root directory `backend`, runtime **Docker** (uses `backend/Dockerfile`).
2. Add all backend environment variables. Set `FRONTEND_URL` to your Vercel URL (no trailing slash; comma-separate multiple).
3. Health check path: `/actuator/health`. Render supplies `PORT`.

## Deploy frontend to Vercel

1. Import the repo, root directory `frontend`, framework preset Vite.
2. Set `VITE_API_URL` to the Render URL (no trailing slash).
3. `vercel.json` rewrites all routes to `index.html` so React Router works on refresh.

## Notes and limits

- Logout discards the token in the browser; tokens are stateless and expire after 12 hours (`JWT_EXPIRATION_MINUTES`).
- Round 2's time limit is shown to teams but not enforced by the server.
- Each team can submit once in Round 2.
- Evaluation runs synchronously during submit (up to about a minute if Gemini is slow); failures are stored, not thrown.
- Gemini judges the generated HTML against the reference image; the server does not render the page or take a screenshot, so visual scoring is based on the code and the image.
- Timeouts are settled when the team's next request arrives, not by a background job, so the admin live view can briefly show an expired clue as current for an idle team.
- **Tie-break decision for organizers:** because completing Round 1 always scores the same (10 points per clue), teams that complete tie on score and are ordered only by earliest completion time (existing rule, unchanged). If wrong answers or time-outs should count against a team, that needs an organizer decision; the place to change it is `RankingService.ordered()`.
- Admin creation happens only through `ADMIN_EMAIL` / `ADMIN_PASSWORD`; registration always creates players.
