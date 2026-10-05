export type Role = "PLAYER" | "ADMIN";

export interface AuthResponse { token: string; role: Role; email: string; teamName: string | null }

export interface Round1Summary {
  status: "NOT_PUBLISHED" | "NOT_STARTED" | "IN_PROGRESS" | "COMPLETED" | "CLOSED";
  completed: number; total: number; correct: number; incorrect: number; timeouts: number; score: number;
}
export interface CellView { open: boolean; number: number | null; letter: string | null }
export interface CrosswordView { rows: number; cols: number; cells: CellView[] }
export interface QuestionView {
  attemptId: number; displayNumber: number; clue: string; direction: "ACROSS" | "DOWN";
  length: number; row: number; col: number; remainingMs: number; totalMs: number;
}
export interface Round1State { summary: Round1Summary; question: QuestionView | null; crossword: CrosswordView | null; lastResult: string | null }

export interface Dashboard {
  teamName: string; captainName: string; captainEmail: string; members: string[];
  currentRound: "ROUND_1" | "ROUND_2" | "ENDED";
  round1: Round1Summary;
  qualificationStatus: "PENDING" | "QUALIFIED" | "NOT_QUALIFIED";
  rank: number | null;
  round2Status: "LOCKED" | "OPEN" | "SUBMITTED" | "NOT_QUALIFIED";
}

export interface Challenge { title: string; prompt: string; requirements: string; timeLimitMinutes: number }
export interface PlayerSubmission { id: number; submittedAt: string; status: string }
export interface Round2View { challenge: Challenge; submission: PlayerSubmission | null; draftHtml: string | null; generationsLeft: number }
export interface GenerateResponse { html: string; generationsLeft: number }

// ---- admin
export interface AdminStats {
  totalTeams: number; totalParticipants: number; round1Active: number; round1Completed: number;
  qualifiedTeams: number; round2Submissions: number; evaluationsCompleted: number; round1Closed: boolean;
}
export interface AdminTeam {
  id: number; name: string; captainName: string; captainEmail: string; members: string[];
  currentRound: string; score: number | null; rank: number | null; qualification: string;
  round1Status: string; round2Status: string;
}
export interface AdminRound1Row {
  teamId: number; teamName: string; status: string; completed: number; total: number;
  currentQuestionNumber: number | null; correct: number; incorrect: number; timeouts: number;
  score: number; rank: number;
}
export interface AdminRanking { rank: number; teamId: number; teamName: string; score: number; correct: number; completed: number; status: string }
export interface AdminQualification { teamId: number; teamName: string; status: string; rank: number | null; score: number }
export interface Evaluation {
  status: "PENDING" | "COMPLETED" | "FAILED";
  requirementScore: number | null; functionalityScore: number | null; uiScore: number | null;
  responsivenessScore: number | null; technicalScore: number | null; accuracyScore: number | null;
  finalScore: number | null; feedback: string | null; errorMessage: string | null; rawResponse: string | null; evaluatedAt: string | null;
}
export interface AdminSubmissionRow {
  id: number; teamId: number; teamName: string; submittedAt: string; evaluationStatus: string; finalScore: number | null;
}
export interface AdminSubmissionDetail {
  id: number; teamId: number; teamName: string; generatedHtml: string | null; notes: string | null;
  submittedAt: string; evaluation: Evaluation | null;
}
export interface AdminResultRow {
  rank: number; submissionId: number; teamId: number; teamName: string; submittedAt: string;
  evaluationStatus: string; finalScore: number | null;
}
export interface AdminQuestion {
  id: number; number: number; clue: string; answer: string; direction: string;
  displayNumber: number | null; row: number | null; col: number | null;
}
export interface AdminQuestionSet {
  published: boolean; layoutReady: boolean; rows: number; cols: number;
  questions: AdminQuestion[]; crossword: CrosswordView | null; sessionsExist: boolean;
}
export interface TeamSequenceRow { teamId: number; teamName: string; sessionStarted: boolean; questionNumbers: number[] }
export interface AdminChallenge {
  id: number; title: string; prompt: string; requirements: string; evaluationPrompt: string;
  timeLimitMinutes: number; published: boolean; hasReferenceImage: boolean; referenceImageName: string | null;
}
