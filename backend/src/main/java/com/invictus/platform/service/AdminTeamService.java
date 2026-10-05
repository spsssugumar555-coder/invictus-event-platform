package com.invictus.platform.service;

import com.invictus.platform.dto.AdminDtos.TeamUpdateRequest;
import com.invictus.platform.dto.AuthDtos.RegisterRequest;
import com.invictus.platform.entity.Team;
import com.invictus.platform.entity.TeamMember;
import com.invictus.platform.entity.UserAccount;
import com.invictus.platform.exception.ApiException;
import com.invictus.platform.repository.QualificationRepository;
import com.invictus.platform.repository.Round1AttemptRepository;
import com.invictus.platform.repository.Round1QueueItemRepository;
import com.invictus.platform.repository.Round1SessionRepository;
import com.invictus.platform.repository.Round2DraftRepository;
import com.invictus.platform.repository.Round2EvaluationRepository;
import com.invictus.platform.repository.Round2SubmissionRepository;
import com.invictus.platform.repository.TeamMemberRepository;
import com.invictus.platform.repository.TeamRepository;
import com.invictus.platform.repository.TeamSequenceItemRepository;
import com.invictus.platform.repository.UserRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ADMIN-only team management. Creating a team applies exactly the same rules as self-registration and creates no
 * Round 1 or Round 2 data. Editing changes identity fields only. Deleting removes the team and everything that
 * references it, in dependency order, inside one transaction.
 */
@Service
public class AdminTeamService {
    private final AuthService auth;
    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final Round1SessionRepository sessions;
    private final Round1AttemptRepository attempts;
    private final Round1QueueItemRepository queue;
    private final TeamSequenceItemRepository sequences;
    private final QualificationRepository qualifications;
    private final Round2SubmissionRepository submissions;
    private final Round2EvaluationRepository evaluations;
    private final Round2DraftRepository drafts;

    public AdminTeamService(AuthService auth, TeamRepository teams, TeamMemberRepository members, UserRepository users,
                            PasswordEncoder encoder, Round1SessionRepository sessions, Round1AttemptRepository attempts,
                            Round1QueueItemRepository queue, TeamSequenceItemRepository sequences,
                            QualificationRepository qualifications, Round2SubmissionRepository submissions,
                            Round2EvaluationRepository evaluations, Round2DraftRepository drafts) {
        this.auth = auth;
        this.teams = teams;
        this.members = members;
        this.users = users;
        this.encoder = encoder;
        this.sessions = sessions;
        this.attempts = attempts;
        this.queue = queue;
        this.sequences = sequences;
        this.qualifications = qualifications;
        this.submissions = submissions;
        this.evaluations = evaluations;
        this.drafts = drafts;
    }

    @Transactional
    public Long create(RegisterRequest request) {
        return auth.createTeam(request).id;
    }

    /** Updates identity fields only. Sessions, attempts, queue, sequence, qualification and Round 2 data are untouched. */
    @Transactional
    public void update(Long id, TeamUpdateRequest r) {
        Team team = find(id);
        UserAccount user = team.user;
        String name = r.teamName().trim();
        String email = r.captainEmail().trim().toLowerCase();
        if (!name.equalsIgnoreCase(team.name) && teams.existsByNameIgnoreCase(name)) {
            throw new ApiException(HttpStatus.CONFLICT, "A team with this name is already registered");
        }
        if (!email.equalsIgnoreCase(user.email) && users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        team.name = name;
        team.captainName = r.captainName().trim();
        team.captainEmail = email;
        user.email = email;
        if (r.password() != null && !r.password().isBlank()) {
            user.passwordHash = encoder.encode(r.password());
        }
        members.deleteAllForTeam(id);
        List<String> names = r.members() == null ? List.of() : r.members();
        for (String n : names) {
            TeamMember m = new TeamMember();
            m.team = team;
            m.name = n.trim();
            members.save(m);
        }
    }

    /**
     * Deletes the team and all of its data. Order matters because of foreign keys:
     * evaluations -> submissions -> drafts -> attempts -> queue items -> sessions -> sequence items ->
     * qualification -> members -> team -> login account. Other teams and the question set are not touched.
     */
    @Transactional
    public void delete(Long id) {
        Team team = find(id);
        Long userId = team.user.id;
        boolean isPlayerAccount = "PLAYER".equals(team.user.role);

        evaluations.deleteAllForTeam(id);
        submissions.deleteAllForTeam(id);
        drafts.deleteAllForTeam(id);
        attempts.deleteAllForTeam(id);
        queue.deleteAllForTeam(id);
        sessions.deleteAllForTeam(id);
        sequences.deleteAllForTeam(id);
        qualifications.deleteAllForTeam(id);
        members.deleteAllForTeam(id);

        teams.deleteById(id);
        teams.flush();
        if (isPlayerAccount) {
            users.deleteById(userId);
        }
    }

    private Team find(Long id) {
        return teams.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Team not found"));
    }
}
