package com.invictus.platform.controller;

import com.invictus.platform.dto.AdminDtos.*;
import com.invictus.platform.dto.AuthDtos.RegisterRequest;
import com.invictus.platform.service.AdminService;
import com.invictus.platform.service.AdminTeamService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/** Every route under /api/admin/** requires ROLE_ADMIN (enforced in SecurityConfig). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService admin;
    private final AdminTeamService teamAdmin;

    public AdminController(AdminService admin, AdminTeamService teamAdmin) {
        this.admin = admin;
        this.teamAdmin = teamAdmin;
    }

    @GetMapping("/dashboard")
    public DashboardStats dashboard() {
        return admin.dashboard();
    }

    @GetMapping("/teams")
    public List<TeamRow> teams() {
        return admin.teams();
    }

    @PostMapping("/teams")
    @ResponseStatus(HttpStatus.CREATED)
    public TeamRow createTeam(@Valid @RequestBody RegisterRequest request) {
        return admin.team(teamAdmin.create(request));
    }

    @PutMapping("/teams/{id}")
    public TeamRow updateTeam(@PathVariable Long id, @Valid @RequestBody TeamUpdateRequest request) {
        teamAdmin.update(id, request);
        return admin.team(id);
    }

    @DeleteMapping("/teams/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTeam(@PathVariable Long id) {
        teamAdmin.delete(id);
    }

    @GetMapping("/round1")
    public List<Round1Row> round1() {
        return admin.round1();
    }

    @GetMapping("/rankings")
    public List<RankingRow> rankings() {
        return admin.rankings();
    }

    @GetMapping("/qualifications")
    public List<QualificationRow> qualifications() {
        return admin.qualifications();
    }

    /** Ends Round 1 and stores QUALIFIED / NOT_QUALIFIED for every team (top 10 qualify). */
    @PostMapping("/qualifications/finalize")
    public List<QualificationRow> finalizeRound1() {
        return admin.finalizeRound1();
    }

    @GetMapping("/round2/submissions")
    public List<SubmissionRow> submissions() {
        return admin.submissions();
    }

    @GetMapping("/round2/submissions/{id}")
    public SubmissionDetail submission(@PathVariable Long id) {
        return admin.submission(id);
    }

    @PostMapping("/round2/evaluate")
    public SubmissionDetail evaluate(@Valid @RequestBody EvaluateRequest request) {
        return admin.evaluate(request.submissionId());
    }
}
