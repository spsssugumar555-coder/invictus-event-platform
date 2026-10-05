package com.invictus.platform.controller;

import com.invictus.platform.dto.PlayerDtos.DashboardDto;
import com.invictus.platform.security.AuthUser;
import com.invictus.platform.service.PlayerService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/player")
public class PlayerController {
    private final PlayerService players;

    public PlayerController(PlayerService players) {
        this.players = players;
    }

    @GetMapping("/dashboard")
    public DashboardDto dashboard(@AuthenticationPrincipal AuthUser user) {
        return players.dashboard(user.teamId());
    }
}
