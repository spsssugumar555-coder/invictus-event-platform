package com.invictus.platform.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "round1_attempts")
public class Round1Attempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne @JoinColumn(name = "session_id", nullable = false)
    public Round1Session session;
    @ManyToOne @JoinColumn(name = "question_id", nullable = false)
    public Question question;
    public String status; // ACTIVE | CORRECT | INCORRECT | SKIPPED | TIMEOUT
    public Instant startedAt;
    public Instant expiresAt;
    public Instant closedAt;
    public String submittedAnswer;
}
