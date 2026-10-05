package com.invictus.platform.controller;

import com.invictus.platform.dto.AdminDtos.QuestionAdminDto;
import com.invictus.platform.dto.AdminDtos.QuestionRequest;
import com.invictus.platform.dto.AdminDtos.QuestionSetDto;
import com.invictus.platform.dto.AdminDtos.ReorderRequest;
import com.invictus.platform.dto.AdminDtos.SequenceRequest;
import com.invictus.platform.dto.AdminDtos.TeamSequenceRow;
import com.invictus.platform.service.AdminRound1Service;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Round 1 content management. Answers are visible here, so every route requires ROLE_ADMIN (SecurityConfig). */
@RestController
@RequestMapping("/api/admin/round1")
public class AdminRound1Controller {
    private final AdminRound1Service admin;

    public AdminRound1Controller(AdminRound1Service admin) {
        this.admin = admin;
    }

    @GetMapping("/questions")
    public QuestionSetDto questions() {
        return admin.set();
    }

    @PostMapping("/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionAdminDto create(@Valid @RequestBody QuestionRequest request) {
        return admin.create(request);
    }

    @PutMapping("/questions/{id}")
    public QuestionAdminDto update(@PathVariable Long id, @Valid @RequestBody QuestionRequest request) {
        return admin.update(id, request);
    }

    @DeleteMapping("/questions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        admin.delete(id);
    }

    @PutMapping("/questions/order")
    public QuestionSetDto reorder(@Valid @RequestBody ReorderRequest request) {
        return admin.reorder(request.questionIds());
    }

    @PostMapping("/crossword/generate")
    public QuestionSetDto generate() {
        return admin.generate();
    }

    @PostMapping("/publish")
    public QuestionSetDto publish() {
        return admin.publish();
    }

    @PostMapping("/unpublish")
    public QuestionSetDto unpublish() {
        return admin.unpublish();
    }

    @GetMapping("/teams")
    public List<TeamSequenceRow> teams() {
        return admin.teamSequences();
    }

    @PostMapping("/team-sequence")
    public List<TeamSequenceRow> setSequence(@Valid @RequestBody SequenceRequest request) {
        return admin.setSequence(request.teamId(), request.questionNumbers());
    }

    @PostMapping("/team-sequence/auto")
    public List<TeamSequenceRow> autoAssign() {
        return admin.autoAssign();
    }
}
