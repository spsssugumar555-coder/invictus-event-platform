package com.invictus.platform.dto;

import jakarta.validation.constraints.Size;
import java.time.Instant;

/** PLAYER-facing Round 2 DTOs: no evaluation prompt, no scores, no feedback, no other teams. */
public final class Round2Dtos {
    private Round2Dtos() {}

    public record ChallengeDto(String title, String prompt, String requirements, int timeLimitMinutes) {}

    public record PlayerSubmissionDto(Long id, Instant submittedAt, String status) {}

    /** draftHtml is the team's own generated page (null once submitted). */
    public record Round2View(ChallengeDto challenge, PlayerSubmissionDto submission, String draftHtml,
                             int generationsLeft) {}

    public record GenerateRequest(@Size(max = 2000, message = "Instruction must be at most 2000 characters") String instruction) {}

    public record GenerateResponse(String html, int generationsLeft) {}

    public record SubmitRequest(@Size(max = 2000, message = "Notes must be at most 2000 characters") String notes) {}
}
