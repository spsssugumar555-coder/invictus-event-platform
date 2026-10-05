package com.invictus.platform.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** A team's latest AI-generated page. Server-held so the browser can never submit arbitrary code as "its build". */
@Entity
@Table(name = "round2_drafts")
public class Round2Draft {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @OneToOne @JoinColumn(name = "team_id", nullable = false)
    public Team team;
    public String html;
    public int generationCount;
    public Instant updatedAt;
}
