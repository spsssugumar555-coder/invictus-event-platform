package com.invictus.platform.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "round2_submissions")
public class Round2Submission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @OneToOne @JoinColumn(name = "team_id", nullable = false)
    public Team team;
    @ManyToOne @JoinColumn(name = "challenge_id", nullable = false)
    public Round2Challenge challenge;
    public String websiteUrl;
    public String githubUrl;
    public String description;
    public String generatedHtml;
    public String status;
    public Instant submittedAt;
}
