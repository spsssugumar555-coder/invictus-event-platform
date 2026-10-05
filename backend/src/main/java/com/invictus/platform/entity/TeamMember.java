package com.invictus.platform.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "team_members")
public class TeamMember {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @ManyToOne @JoinColumn(name = "team_id", nullable = false)
    public Team team;
    public String name;
}
