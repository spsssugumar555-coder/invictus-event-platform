package com.invictus.platform.service;

import com.invictus.platform.ai.AiClient;
import com.invictus.platform.ai.AiEvaluationService;
import com.invictus.platform.ai.GeminiException;
import com.invictus.platform.dto.Round2Dtos.ChallengeDto;
import com.invictus.platform.dto.Round2Dtos.GenerateResponse;
import com.invictus.platform.dto.Round2Dtos.PlayerSubmissionDto;
import com.invictus.platform.dto.Round2Dtos.Round2View;
import com.invictus.platform.entity.Qualification;
import com.invictus.platform.entity.Round2Challenge;
import com.invictus.platform.entity.Round2Draft;
import com.invictus.platform.entity.Round2ReferenceImage;
import com.invictus.platform.entity.Round2Submission;
import com.invictus.platform.entity.Team;
import com.invictus.platform.exception.ApiException;
import com.invictus.platform.repository.QualificationRepository;
import com.invictus.platform.repository.Round2ChallengeRepository;
import com.invictus.platform.repository.Round2DraftRepository;
import com.invictus.platform.repository.Round2ReferenceImageRepository;
import com.invictus.platform.repository.Round2SubmissionRepository;
import com.invictus.platform.repository.TeamRepository;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Player-side Round 2. Every method first checks that the caller's team is QUALIFIED and that the challenge is
 * published. The player never receives the evaluation prompt, scores, feedback or anything about other teams.
 */
@Service
public class Round2Service {
    private static final Logger log = LoggerFactory.getLogger(Round2Service.class);
    private static final int MAX_HTML_CHARS = 300_000;

    private static final String BUILDER_SYSTEM = """
            You are an expert front-end engineer. Recreate the website shown in the reference image as ONE
            self-contained HTML document: inline <style> and (if needed) inline <script>, no build step, no external
            libraries, no external network requests except optional Google Fonts. Match layout, spacing, colors,
            typography, components and buttons as closely as you can, use placeholder blocks or inline SVG instead of
            photos, and make the page responsive with a viewport meta tag and CSS media queries.
            This is a UI-only task: do not write or call any backend, database, API, authentication or deployment
            code; use static placeholder data where the design shows dynamic content.
            If CURRENT HTML is provided, improve it according to the participant instruction and return the full
            updated document. Return ONLY the HTML document, starting with <!DOCTYPE html>, with no explanation and
            no markdown fences.
            """;

    private final QualificationRepository qualifications;
    private final Round2ChallengeRepository challenges;
    private final Round2SubmissionRepository submissions;
    private final Round2DraftRepository drafts;
    private final Round2ReferenceImageRepository images;
    private final TeamRepository teams;
    private final AiClient ai;
    private final AiEvaluationService evaluation;
    private final int maxGenerations;
    /** Teams that currently have a generation running: at most one Gemini call per team at a time (bounded, no queue). */
    private final Set<Long> generating = ConcurrentHashMap.newKeySet();

    public Round2Service(QualificationRepository qualifications, Round2ChallengeRepository challenges,
                         Round2SubmissionRepository submissions, Round2DraftRepository drafts,
                         Round2ReferenceImageRepository images, TeamRepository teams, AiClient ai,
                         AiEvaluationService evaluation,
                         @Value("${app.round2.max-generations:40}") int maxGenerations) {
        this.qualifications = qualifications;
        this.challenges = challenges;
        this.submissions = submissions;
        this.drafts = drafts;
        this.images = images;
        this.teams = teams;
        this.ai = ai;
        this.evaluation = evaluation;
        this.maxGenerations = maxGenerations;
    }

    public Round2View view(Long teamId) {
        requireQualified(teamId);
        Round2Challenge c = publishedChallenge();
        PlayerSubmissionDto sub = submissions.findByTeamId(teamId).map(Round2Service::toDto).orElse(null);
        Round2Draft draft = drafts.findByTeamId(teamId).orElse(null);
        int used = draft == null ? 0 : draft.generationCount;
        return new Round2View(
                new ChallengeDto(c.title, c.description, c.requirements == null ? "" : c.requirements, c.timeLimitMinutes),
                sub, sub != null || draft == null ? null : draft.html, Math.max(0, maxGenerations - used));
    }

    public Round2ReferenceImage referenceImage(Long teamId) {
        requireQualified(teamId);
        Round2Challenge c = publishedChallenge();
        return images.findByChallengeId(c.id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No reference image"));
    }

    /** Calls Gemini on the server with the reference image and challenge prompt; stores the result as the team's draft. */
    public GenerateResponse generate(Long teamId, String instruction) {
        requireQualified(teamId);
        // One generation per team at a time: stops double-clicks from racing the generation counter or stacking
        // Gemini calls. Other teams are unaffected; the set is bounded by the number of teams.
        if (!generating.add(teamId)) {
            throw new ApiException(HttpStatus.CONFLICT, "A generation is already running for your team. Please wait for it to finish.");
        }
        try {
            return doGenerate(teamId, instruction);
        } finally {
            generating.remove(teamId);
        }
    }

    private GenerateResponse doGenerate(Long teamId, String instruction) {
        Round2Challenge c = publishedChallenge();
        if (submissions.findByTeamId(teamId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Your team has already submitted");
        }
        Round2ReferenceImage image = images.findByChallengeId(c.id)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "The reference image is not available yet"));
        Team team = teams.findById(teamId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Team not found"));
        Round2Draft draft = drafts.findByTeamId(teamId).orElseGet(() -> {
            Round2Draft d = new Round2Draft();
            d.team = team;
            return d;
        });
        if (draft.generationCount >= maxGenerations) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "You have used all of your AI generations");
        }

        StringBuilder prompt = new StringBuilder("PARTICIPANT CHALLENGE PROMPT:\n").append(c.description).append('\n');
        if (c.requirements != null && !c.requirements.isBlank()) {
            prompt.append("\nADDITIONAL REQUIREMENTS:\n").append(c.requirements).append('\n');
        }
        if (draft.html != null && !draft.html.isBlank()) {
            prompt.append("\nCURRENT HTML:\n").append(draft.html).append('\n');
        }
        if (instruction != null && !instruction.isBlank()) {
            prompt.append("\nPARTICIPANT INSTRUCTION:\n").append(instruction.trim()).append('\n');
        }

        String html;
        try {
            html = extractHtml(ai.generate(BUILDER_SYSTEM, prompt.toString(), image.data, image.contentType, false));
        } catch (ApiException e) {
            throw e;
        } catch (GeminiException e) {
            log.warn("AI builder failed for team {}: {}", teamId, e.getMessage()); // message is key-free
            throw toApiException(e);
        } catch (Exception e) {
            log.warn("AI builder failed for team {}: {}", teamId, e.toString());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI builder is unavailable right now. Please try again in a moment.");
        }
        draft.html = html;
        draft.generationCount++;
        draft.updatedAt = Instant.now();
        drafts.save(draft);
        return new GenerateResponse(html, Math.max(0, maxGenerations - draft.generationCount));
    }

    /** Submits the team's latest server-held draft. The browser never sends the code that gets judged. */
    public PlayerSubmissionDto submit(Long teamId, String notes) {
        requireQualified(teamId);
        Round2Challenge c = publishedChallenge();
        if (submissions.findByTeamId(teamId).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "Your team has already submitted");
        }
        Round2Draft draft = drafts.findByTeamId(teamId).orElse(null);
        if (draft == null || draft.html == null || draft.html.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Generate your website before submitting");
        }
        Team team = teams.findById(teamId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Team not found"));
        Round2Submission s = new Round2Submission();
        s.team = team;
        s.challenge = c;
        s.generatedHtml = draft.html;
        s.description = notes == null || notes.isBlank() ? null : notes.trim();
        s.status = "SUBMITTED";
        s.submittedAt = Instant.now();
        s = submissions.save(s);
        try {
            evaluation.evaluate(s.id);
        } catch (Exception e) {
            log.warn("Evaluation could not be started for submission {}: {}", s.id, e.toString());
        }
        return toDto(s);
    }

    // ------------------------------------------------------------ helpers

    private void requireQualified(Long teamId) {
        Qualification q = qualifications.findByTeamId(teamId).orElse(null);
        if (q == null || !"QUALIFIED".equals(q.status)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Round 2 is only available to qualified teams");
        }
    }

    private Round2Challenge publishedChallenge() {
        return challenges.findFirstByActiveTrueOrderByIdAsc()
                .filter(c -> c.published)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "The Round 2 challenge has not been published yet"));
    }

    /** Turns a classified Gemini failure into a clean message for the player. Raw exceptions never reach the browser. */
    static ApiException toApiException(GeminiException e) {
        return switch (e.getKind()) {
            case UNAVAILABLE, RATE_LIMITED -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Gemini is temporarily unavailable. Please try again shortly.");
            case AUTH -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "The AI builder is not configured correctly (the Gemini API key is missing or was rejected). Please tell the organizers.");
            case MODEL_NOT_FOUND, NOT_CONFIGURED -> new ApiException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "The AI builder is not configured correctly (the Gemini model is missing or was not found). Please tell the organizers.");
            case BLOCKED -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Gemini declined to generate this page. Try rephrasing your instruction.");
            case TRUNCATED -> new ApiException(HttpStatus.BAD_GATEWAY,
                    "The page was too large for the AI to finish. Ask for a simpler page or fewer sections, then try again.");
            case EMPTY -> new ApiException(HttpStatus.BAD_GATEWAY,
                    "The AI did not return anything this time. Please try again.");
            default -> new ApiException(HttpStatus.BAD_GATEWAY,
                    "The AI builder returned an unusable response. Please try again.");
        };
    }

    static String extractHtml(String raw) {
        String html = HtmlExtractor.extract(raw);
        if (html == null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The AI did not return a web page. Please try again.");
        }
        if (html.length() > MAX_HTML_CHARS) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "The generated page was too large. Ask for a simpler page.");
        }
        return html;
    }

    private static PlayerSubmissionDto toDto(Round2Submission s) {
        return new PlayerSubmissionDto(s.id, s.submittedAt, s.status);
    }
}
