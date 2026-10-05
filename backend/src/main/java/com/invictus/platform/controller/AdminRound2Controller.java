package com.invictus.platform.controller;

import com.invictus.platform.dto.AdminDtos.ChallengeAdminDto;
import com.invictus.platform.dto.AdminDtos.ChallengeRequest;
import com.invictus.platform.dto.AdminDtos.ResultRow;
import com.invictus.platform.entity.Round2ReferenceImage;
import com.invictus.platform.service.AdminRound2Service;
import com.invictus.platform.service.AdminService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/** Round 2 content + results. Includes the internal evaluation prompt, so every route requires ROLE_ADMIN. */
@RestController
@RequestMapping("/api/admin/round2")
public class AdminRound2Controller {
    private final AdminRound2Service challenge;
    private final AdminService admin;

    public AdminRound2Controller(AdminRound2Service challenge, AdminService admin) {
        this.challenge = challenge;
        this.admin = admin;
    }

    @GetMapping("/challenge")
    public ChallengeAdminDto get() {
        return challenge.get();
    }

    @PutMapping("/challenge")
    public ChallengeAdminDto update(@Valid @RequestBody ChallengeRequest request) {
        return challenge.update(request);
    }

    @PostMapping(value = "/reference-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ChallengeAdminDto upload(@RequestParam("file") MultipartFile file) {
        return challenge.uploadImage(file);
    }

    @GetMapping("/reference-image")
    public ResponseEntity<byte[]> image() {
        Round2ReferenceImage img = challenge.image();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(img.contentType))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(img.data);
    }

    @PostMapping("/publish")
    public ChallengeAdminDto publish() {
        return challenge.publish();
    }

    @PostMapping("/unpublish")
    public ChallengeAdminDto unpublish() {
        return challenge.unpublish();
    }

    @GetMapping("/results")
    public List<ResultRow> results() {
        return admin.results();
    }
}
