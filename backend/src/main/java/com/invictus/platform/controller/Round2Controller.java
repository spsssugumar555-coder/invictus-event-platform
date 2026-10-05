package com.invictus.platform.controller;

import com.invictus.platform.dto.Round2Dtos.GenerateRequest;
import com.invictus.platform.dto.Round2Dtos.GenerateResponse;
import com.invictus.platform.dto.Round2Dtos.PlayerSubmissionDto;
import com.invictus.platform.dto.Round2Dtos.Round2View;
import com.invictus.platform.dto.Round2Dtos.SubmitRequest;
import com.invictus.platform.entity.Round2ReferenceImage;
import com.invictus.platform.security.AuthUser;
import com.invictus.platform.service.Round2Service;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Player Round 2 API. Responses never contain evaluation data. All routes require ROLE_PLAYER and a qualified team. */
@RestController
@RequestMapping("/api/round2")
public class Round2Controller {
    private final Round2Service round2;

    public Round2Controller(Round2Service round2) {
        this.round2 = round2;
    }

    @GetMapping
    public Round2View view(@AuthenticationPrincipal AuthUser user) {
        return round2.view(user.teamId());
    }

    @GetMapping("/reference-image")
    public ResponseEntity<byte[]> referenceImage(@AuthenticationPrincipal AuthUser user) {
        Round2ReferenceImage img = round2.referenceImage(user.teamId());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(img.contentType))
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(img.data);
    }

    @PostMapping("/generate")
    public GenerateResponse generate(@AuthenticationPrincipal AuthUser user,
                                     @Valid @RequestBody(required = false) GenerateRequest request) {
        return round2.generate(user.teamId(), request == null ? null : request.instruction());
    }

    @PostMapping("/submit")
    @ResponseStatus(HttpStatus.CREATED)
    public PlayerSubmissionDto submit(@AuthenticationPrincipal AuthUser user,
                                      @Valid @RequestBody(required = false) SubmitRequest request) {
        return round2.submit(user.teamId(), request == null ? null : request.notes());
    }
}
