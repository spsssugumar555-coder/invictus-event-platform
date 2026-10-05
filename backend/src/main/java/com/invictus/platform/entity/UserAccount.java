package com.invictus.platform.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "users")
public class UserAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String email;
    public String passwordHash;
    public String role; // PLAYER | ADMIN
    public Instant createdAt;
}
