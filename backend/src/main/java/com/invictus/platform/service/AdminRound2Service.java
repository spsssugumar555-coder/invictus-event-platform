package com.invictus.platform.service;

import com.invictus.platform.dto.AdminDtos.ChallengeAdminDto;
import com.invictus.platform.dto.AdminDtos.ChallengeRequest;
import com.invictus.platform.entity.Round2Challenge;
import com.invictus.platform.entity.Round2ReferenceImage;
import com.invictus.platform.exception.ApiException;
import com.invictus.platform.repository.Round2ChallengeRepository;
import com.invictus.platform.repository.Round2ReferenceImageRepository;
import com.invictus.platform.repository.Round2SubmissionRepository;
import java.io.IOException;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** ADMIN-only management of the Round 2 challenge, reference image, and the two separate prompts. */
@Service
public class AdminRound2Service {
    static final long MAX_IMAGE_BYTES = 5L * 1024 * 1024;

    private final Round2ChallengeRepository challenges;
    private final Round2ReferenceImageRepository images;
    private final Round2SubmissionRepository submissions;

    public AdminRound2Service(Round2ChallengeRepository challenges, Round2ReferenceImageRepository images,
                              Round2SubmissionRepository submissions) {
        this.challenges = challenges;
        this.images = images;
        this.submissions = submissions;
    }

    @Transactional
    public ChallengeAdminDto get() {
        return dto(challenge());
    }

    @Transactional
    public ChallengeAdminDto update(ChallengeRequest r) {
        Round2Challenge c = challenge();
        requireUnpublished(c);
        if (r.timeLimitMinutes() < 1 || r.timeLimitMinutes() > 600) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Time limit must be between 1 and 600 minutes");
        }
        c.title = r.title().trim();
        c.description = r.prompt().trim();
        c.requirements = r.requirements() == null ? "" : r.requirements().trim();
        c.evaluationPrompt = r.evaluationPrompt().trim();
        c.timeLimitMinutes = r.timeLimitMinutes();
        return dto(c);
    }

    @Transactional
    public ChallengeAdminDto uploadImage(MultipartFile file) {
        Round2Challenge c = challenge();
        requireUnpublished(c);
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Choose an image file");
        }
        if (file.getSize() > MAX_IMAGE_BYTES) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "The image must be 5 MB or smaller");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The image could not be read");
        }
        String type = sniff(data);
        if (type == null) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Only PNG, JPEG or WEBP images are supported");
        }
        Round2ReferenceImage img = images.findByChallengeId(c.id).orElseGet(Round2ReferenceImage::new);
        img.challenge = c;
        img.filename = safeName(file.getOriginalFilename());
        img.contentType = type;
        img.data = data;
        img.uploadedAt = Instant.now();
        images.save(img);
        return dto(c);
    }

    @Transactional(readOnly = true)
    public Round2ReferenceImage image() {
        Round2Challenge c = challenges.findFirstByActiveTrueOrderByIdAsc()
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No challenge"));
        return images.findByChallengeId(c.id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "No reference image"));
    }

    @Transactional
    public ChallengeAdminDto publish() {
        Round2Challenge c = challenge();
        if (images.findByChallengeId(c.id).isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Upload a reference image before publishing");
        }
        if (isBlank(c.description) || isBlank(c.evaluationPrompt) || isBlank(c.title)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Fill in the challenge prompt and the evaluation prompt before publishing");
        }
        c.published = true;
        return dto(c);
    }

    @Transactional
    public ChallengeAdminDto unpublish() {
        Round2Challenge c = challenge();
        if (submissions.count() > 0) {
            throw new ApiException(HttpStatus.CONFLICT, "Teams have already submitted, so the challenge can no longer be unpublished");
        }
        c.published = false;
        return dto(c);
    }

    // ------------------------------------------------------------ helpers

    private Round2Challenge challenge() {
        return challenges.findFirstByActiveTrueOrderByIdAsc().orElseGet(() -> {
            Round2Challenge c = new Round2Challenge();
            c.title = "Recreate the reference website";
            c.description = "";
            c.requirements = "";
            c.evaluationPrompt = "";
            c.timeLimitMinutes = 90;
            c.active = true;
            c.published = false;
            return challenges.save(c);
        });
    }

    private void requireUnpublished(Round2Challenge c) {
        if (c.published) {
            throw new ApiException(HttpStatus.CONFLICT, "Unpublish the challenge before changing it");
        }
    }

    private ChallengeAdminDto dto(Round2Challenge c) {
        Round2ReferenceImage img = images.findByChallengeId(c.id).orElse(null);
        return new ChallengeAdminDto(c.id, c.title, c.description, c.requirements, c.evaluationPrompt,
                c.timeLimitMinutes, c.published, img != null, img == null ? null : img.filename);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** Detects the real type from the file's magic bytes; the client-supplied content type is never trusted. */
    static String sniff(byte[] d) {
        if (d.length > 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return "image/png";
        if (d.length > 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (d.length > 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') return "image/webp";
        return null;
    }

    private static String safeName(String original) {
        String n = original == null ? "reference" : original.replaceAll("[\\\\/]+", "_").replaceAll("[^A-Za-z0-9._ -]", "");
        if (n.isBlank()) n = "reference";
        return n.length() > 100 ? n.substring(0, 100) : n;
    }
}
