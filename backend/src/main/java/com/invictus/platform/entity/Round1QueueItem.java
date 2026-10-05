package com.invictus.platform.entity;

import jakarta.persistence.*;

/** One question in one team's queue. Lowest sortPos among non-completed items is the next question. */
@Entity
@Table(name = "round1_queue_items")
public class Round1QueueItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne @JoinColumn(name = "session_id", nullable = false)
    public Round1Session session;
    @ManyToOne @JoinColumn(name = "question_id", nullable = false)
    public Question question;
    public int sortPos;
    public boolean completed;
}
