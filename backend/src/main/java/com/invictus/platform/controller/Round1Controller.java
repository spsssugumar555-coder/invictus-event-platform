package com.invictus.platform.controller;

import com.invictus.platform.dto.Round1Dtos.AnswerRequest;
import com.invictus.platform.dto.Round1Dtos.Round1State;
import com.invictus.platform.dto.Round1Dtos.Round1Summary;
import com.invictus.platform.security.AuthUser;
import com.invictus.platform.service.Round1Service;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** Player Round 1 API. There is intentionally no skip or "next" endpoint: the server decides what comes next. */
@RestController
@RequestMapping("/api/round1")
public class Round1Controller {
    private final Round1Service round1;

    public Round1Controller(Round1Service round1) {
        this.round1 = round1;
    }

    /** Returns the current question and the crossword; the first call also starts the team's session. */
    @GetMapping("/current")
    public Round1State current(@AuthenticationPrincipal AuthUser user) {
        return round1.current(user.teamId());
    }

    @PostMapping("/answer")
    public Round1State answer(@AuthenticationPrincipal AuthUser user, @Valid @RequestBody AnswerRequest request) {
        return round1.answer(user.teamId(), request.attemptId(), request.answer());
    }

    @GetMapping("/status")
    public Round1Summary status(@AuthenticationPrincipal AuthUser user) {
        return round1.summary(user.teamId());
    }
}
