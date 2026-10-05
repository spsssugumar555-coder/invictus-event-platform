package com.invictus.platform.service;

import com.invictus.platform.dto.AuthDtos.AuthResponse;
import com.invictus.platform.dto.AuthDtos.LoginRequest;
import com.invictus.platform.dto.AuthDtos.RegisterRequest;
import com.invictus.platform.entity.Team;
import com.invictus.platform.entity.TeamMember;
import com.invictus.platform.entity.UserAccount;
import com.invictus.platform.exception.ApiException;
import com.invictus.platform.repository.TeamMemberRepository;
import com.invictus.platform.repository.TeamRepository;
import com.invictus.platform.repository.UserRepository;
import com.invictus.platform.security.JwtService;
import java.time.Instant;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final TeamRepository teams;
    private final TeamMemberRepository members;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    public AuthService(UserRepository users, TeamRepository teams, TeamMemberRepository members,
                       PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.teams = teams;
        this.members = members;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional
    public AuthResponse register(RegisterRequest r) {
        Team team = createTeam(r);
        UserAccount user = team.user;
        return new AuthResponse(jwt.generate(user.id, user.email, user.role, team.id), user.role, user.email, team.name);
    }

    /** Creates the player account, team and members. Shared by self-registration and admin team creation. */
    @Transactional
    public Team createTeam(RegisterRequest r) {
        String email = r.captainEmail().trim().toLowerCase();
        String teamName = r.teamName().trim();
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        if (teams.existsByNameIgnoreCase(teamName)) {
            throw new ApiException(HttpStatus.CONFLICT, "A team with this name is already registered");
        }

        UserAccount user = new UserAccount();
        user.email = email;
        user.passwordHash = encoder.encode(r.password());
        user.role = "PLAYER";
        user.createdAt = Instant.now();
        user = users.save(user);

        Team team = new Team();
        team.name = teamName;
        team.captainName = r.captainName().trim();
        team.captainEmail = email;
        team.user = user;
        team.createdAt = Instant.now();
        team = teams.save(team);

        List<String> names = r.members() == null ? List.of() : r.members();
        for (String n : names) {
            TeamMember m = new TeamMember();
            m.team = team;
            m.name = n.trim();
            members.save(m);
        }
        return team;
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest r) {
        UserAccount user = users.findByEmailIgnoreCase(r.email().trim()).orElse(null);
        if (user == null || !encoder.matches(r.password(), user.passwordHash)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        Team team = "PLAYER".equals(user.role) ? teams.findByUserId(user.id).orElse(null) : null;
        if ("PLAYER".equals(user.role) && team == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        return new AuthResponse(
                jwt.generate(user.id, user.email, user.role, team == null ? null : team.id),
                user.role, user.email, team == null ? null : team.name);
    }
}
