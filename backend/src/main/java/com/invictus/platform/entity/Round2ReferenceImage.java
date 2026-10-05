package com.invictus.platform.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "round2_reference_images")
public class Round2ReferenceImage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @OneToOne @JoinColumn(name = "challenge_id", nullable = false)
    public Round2Challenge challenge;
    public String filename;
    public String contentType;
    public byte[] data;
    public Instant uploadedAt;
}
