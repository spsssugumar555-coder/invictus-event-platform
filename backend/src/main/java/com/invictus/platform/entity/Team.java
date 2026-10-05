package com.invictus.platform.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "teams")
public class Team {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String name;
    public String captainName;
    public String captainEmail;
    @OneToOne @JoinColumn(name = "user_id", nullable = false)
    public UserAccount user;
    public Instant createdAt;
}
