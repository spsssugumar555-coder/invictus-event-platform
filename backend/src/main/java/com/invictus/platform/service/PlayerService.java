package com.invictus.platform.service;

import com.invictus.platform.dto.PlayerDtos.DashboardDto;
import com.invictus.platform.entity.Qualification;
import com.invictus.platform.entity.Team;
import com.invictus.platform.exception.ApiException;
import com.invictus.platform.repository.QualificationRepository;
import com.invictus.platform.repository.Round2SubmissionRepository;
import com.invictus.platform.repository.TeamMemberRepository;
import com.invictus.platform.repository.TeamRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerService {
    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final QualificationRepository qualifications;
    private final Round2SubmissionRepository submissions;
    private final Round1Service round1;

    public PlayerService(TeamRepository teams, TeamMemberRepository members, QualificationRepository qualifications,
                         Round2SubmissionRepository submissions, Round1Service round1) {
        this.teams = teams;
        this.members = members;
        this.qualifications = qualifications;
        this.submissions = submissions;
        this.round1 = round1;
    }

    @Transactional(readOnly = true)
    public DashboardDto dashboard(Long teamId) {
        Team team = teams.findById(teamId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Team not found"));
        Qualification q = qualifications.findByTeamId(teamId).orElse(null);
        String qualification = q == null ? "PENDING" : q.status;
        boolean hasSubmission = submissions.findByTeamId(teamId).isPresent();

        String round2;
        String currentRound;
        switch (qualification) {
            case "QUALIFIED" -> {
                round2 = hasSubmission ? "SUBMITTED" : "OPEN";
                currentRound = "ROUND_2";
            }
            case "NOT_QUALIFIED" -> {
                round2 = "NOT_QUALIFIED";
                currentRound = "ENDED";
            }
            default -> {
                round2 = "LOCKED";
                currentRound = "ROUND_1";
            }
        }
        return new DashboardDto(
                team.name, team.captainName, team.captainEmail,
                members.findByTeamId(teamId).stream().map(m -> m.name).toList(),
                currentRound, round1.summary(teamId), qualification,
                q == null ? null : q.rankNo, round2);
    }
}
