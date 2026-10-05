package com.invictus.platform.entity;

import jakarta.persistence.*;

/** Admin-configured starting order for a team (applied when the team's Round 1 session is created). */
@Entity
@Table(name = "team_sequence_items")
public class TeamSequenceItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne @JoinColumn(name = "team_id", nullable = false)
    public Team team;
    @ManyToOne @JoinColumn(name = "question_id", nullable = false)
    public Question question;
    public int sortPos;
}
