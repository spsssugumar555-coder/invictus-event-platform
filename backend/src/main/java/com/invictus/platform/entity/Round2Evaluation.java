package com.invictus.platform.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "round2_evaluations")
public class Round2Evaluation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @OneToOne @JoinColumn(name = "submission_id", nullable = false)
    public Round2Submission submission;
    public String status; // PENDING | COMPLETED | FAILED
    public Integer requirementScore;
    public Integer functionalityScore;
    public Integer uiScore;
    public Integer responsivenessScore;
    public Integer technicalScore;
    public Integer accuracyScore;
    public Double finalScore;
    public String feedback;
    public String errorMessage;
    public String rawResponse;
    public Instant evaluatedAt;
}
