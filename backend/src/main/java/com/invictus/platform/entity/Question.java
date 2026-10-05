package com.invictus.platform.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "questions")
public class Question {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "question_number")
    public Integer number;
    public String clue;
    public String answer; // never sent to clients
    public String direction;
    public Integer gridRow;
    public Integer gridCol;
    public Integer displayNumber;
}
