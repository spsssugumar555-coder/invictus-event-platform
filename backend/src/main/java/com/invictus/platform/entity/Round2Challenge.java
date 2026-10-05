package com.invictus.platform.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "round2_challenges")
public class Round2Challenge {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String title;
    public String description;
    public String requirements;
    public int timeLimitMinutes;
    public String evaluationPrompt; // internal: never sent to players
    public boolean published;
    public boolean active;
}
