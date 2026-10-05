package com.invictus.platform.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank(message = "Team name is required") @Size(max = 100, message = "Team name is too long") String teamName,
            @NotBlank(message = "Captain name is required") @Size(max = 100, message = "Captain name is too long") String captainName,
            @NotBlank(message = "Captain email is required") @Email(message = "Enter a valid email") @Size(max = 255) String captainEmail,
            @Size(max = 4, message = "A team can have at most 4 additional members") List<@NotBlank(message = "Member names cannot be blank") @Size(max = 100) String> members,
            @NotBlank(message = "Password is required") @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String password) {}

    public record LoginRequest(
            @NotBlank(message = "Email is required") String email,
            @NotBlank(message = "Password is required") String password) {}

    public record AuthResponse(String token, String role, String email, String teamName) {}
}
