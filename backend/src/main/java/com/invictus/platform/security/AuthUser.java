package com.invictus.platform.security;

/** Authenticated principal built from a verified JWT. teamId is null for admins. */
public record AuthUser(Long userId, String email, String role, Long teamId) {}
