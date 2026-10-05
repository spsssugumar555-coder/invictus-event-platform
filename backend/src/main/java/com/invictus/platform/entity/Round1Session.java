package com.invictus.platform.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "round1_sessions")
public class Round1Session {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @OneToOne @JoinColumn(name = "team_id", nullable = false)
    public Team team;
    public String status; // IN_PROGRESS | COMPLETED
    public Instant startedAt;
    public Instant completedAt;
    public int correctCount;
    public int incorrectCount;
    public int skippedCount;
    public int timeoutCount;
    public int score;

}
