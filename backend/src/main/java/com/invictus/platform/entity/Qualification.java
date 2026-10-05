package com.invictus.platform.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "qualifications")
public class Qualification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @OneToOne @JoinColumn(name = "team_id", nullable = false)
    public Team team;
    public String status; // QUALIFIED | NOT_QUALIFIED
    public Integer rankNo;
    public int score;
    public Instant decidedAt;
}
