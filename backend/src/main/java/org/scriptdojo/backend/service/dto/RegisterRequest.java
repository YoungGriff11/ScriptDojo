package org.scriptdojo.backend.service.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 20, message = "Username must be 3–20 characters")
        String username,

        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        String password,

        @Email(message = "Invalid email format")
        @NotBlank(message = "Email is required")
        String email
) {}
