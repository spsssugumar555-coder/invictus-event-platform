package com.invictus.platform.dto;

import java.util.List;

public final class PlayerDtos {
    private PlayerDtos() {}

    /** Everything a player may know about their own team. No evaluation data, no other teams. */
    public record DashboardDto(
            String teamName,
            String captainName,
            String captainEmail,
            List<String> members,
            String currentRound,          // ROUND_1 | ROUND_2 | ENDED
            Round1Dtos.Round1Summary round1,
            String qualificationStatus,   // PENDING | QUALIFIED | NOT_QUALIFIED
            Integer rank,                 // own rank, only once qualification is published
            String round2Status) {}       // LOCKED | OPEN | SUBMITTED | NOT_QUALIFIED
}
