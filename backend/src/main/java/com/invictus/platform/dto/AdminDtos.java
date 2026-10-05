package com.invictus.platform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class AdminDtos {
    private AdminDtos() {}

    public record DashboardStats(long totalTeams, long totalParticipants, long round1Active, long round1Completed,
                                 long qualifiedTeams, long round2Submissions, long evaluationsCompleted,
                                 boolean round1Closed) {}

    public record TeamRow(Long id, String name, String captainName, String captainEmail, List<String> members,
                          String currentRound, Integer score, Integer rank, String qualification,
                          String round1Status, String round2Status) {}

    /** Edit a team. password is optional: omit it to keep the current one. Progress is never touched. */
    public record TeamUpdateRequest(
            @NotBlank(message = "Team name is required") @Size(max = 100, message = "Team name is too long") String teamName,
            @NotBlank(message = "Captain name is required") @Size(max = 100, message = "Captain name is too long") String captainName,
            @NotBlank(message = "Captain email is required") @Email(message = "Enter a valid email") @Size(max = 255) String captainEmail,
            @Size(max = 4, message = "A team can have at most 4 additional members") List<@NotBlank(message = "Member names cannot be blank") @Size(max = 100) String> members,
            @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String password) {}

    public record Round1Row(Long teamId, String teamName, String status, int completed, int total,
                            Integer currentQuestionNumber, int correct, int incorrect, int timeouts,
                            int score, int rank) {}

    public record RankingRow(int rank, Long teamId, String teamName, int score, int correct, int completed,
                             String status) {}

    public record QualificationRow(Long teamId, String teamName, String status, Integer rank, int score) {}

    public record EvaluationDto(String status, Integer requirementScore, Integer functionalityScore, Integer uiScore,
                                Integer responsivenessScore, Integer technicalScore, Integer accuracyScore,
                                Double finalScore, String feedback, String errorMessage, String rawResponse, Instant evaluatedAt) {}

    public record SubmissionRow(Long id, Long teamId, String teamName, Instant submittedAt,
                                String evaluationStatus, Double finalScore) {}

    /** generatedHtml is the page the team built with the AI builder. notes is the team's optional note. */
    public record SubmissionDetail(Long id, Long teamId, String teamName, String generatedHtml, String notes,
                                   Instant submittedAt, EvaluationDto evaluation) {}

    public record ResultRow(int rank, Long submissionId, Long teamId, String teamName, Instant submittedAt,
                            String evaluationStatus, Double finalScore) {}

    public record EvaluateRequest(@NotNull(message = "submissionId is required") Long submissionId) {}

    // ---------- Round 1 question management (answers are visible here, ADMIN only)
    public record QuestionRequest(
            @NotBlank(message = "Clue is required") @Size(max = 500, message = "Clue must be at most 500 characters") String clue,
            @NotBlank(message = "Answer is required") @Size(max = 40, message = "Answer is too long") String answer) {}

    public record QuestionAdminDto(Long id, int number, String clue, String answer, String direction,
                                   Integer displayNumber, Integer row, Integer col) {}

    public record QuestionSetDto(boolean published, boolean layoutReady, int rows, int cols,
                                 List<QuestionAdminDto> questions, Round1Dtos.CrosswordView crossword,
                                 boolean sessionsExist) {}

    public record ReorderRequest(@NotEmpty(message = "questionIds is required") List<Long> questionIds) {}

    public record SequenceRequest(@NotNull(message = "teamId is required") Long teamId,
                                  @NotNull(message = "questionNumbers is required") List<Integer> questionNumbers) {}

    public record TeamSequenceRow(Long teamId, String teamName, boolean sessionStarted, List<Integer> questionNumbers) {}

    // ---------- Round 2 challenge management (includes the internal evaluation prompt, ADMIN only)
    public record ChallengeAdminDto(Long id, String title, String prompt, String requirements, String evaluationPrompt,
                                    int timeLimitMinutes, boolean published, boolean hasReferenceImage,
                                    String referenceImageName) {}

    public record ChallengeRequest(
            @NotBlank(message = "Title is required") @Size(max = 200) String title,
            @NotBlank(message = "Challenge prompt is required") @Size(max = 4000) String prompt,
            @Size(max = 4000) String requirements,
            @NotBlank(message = "Evaluation prompt is required") @Size(max = 6000) String evaluationPrompt,
            @NotNull(message = "Time limit is required") Integer timeLimitMinutes) {}
}
