package com.invictus.platform.entity;

import jakarta.persistence.*;
import java.time.Instant;

/** Single row (id = 1): whether the admin has published the question set, and the crossword grid size. */
@Entity
@Table(name = "round1_config")
public class Round1Config {
    @Id
    public Long id;
    public boolean published;
    public int gridRows;
    public int gridCols;
    public Instant publishedAt;
}
