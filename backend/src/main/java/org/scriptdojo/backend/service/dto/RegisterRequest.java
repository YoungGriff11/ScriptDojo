package org.scriptdojo.backend.service.dto;

import jakarta.validation.constraints.*;

/**
 * Immutable record representing the request body for POST /api/auth/register.
 * Validated automatically by Jakarta Bean Validation before
 * {@link org.scriptdojo.backend.controller.AuthController#register} is invoked —
 * any constraint violation results in a 400 Bad Request before the method body executes.
 * Field values are passed directly to a new {@link org.scriptdojo.backend.entity.UserEntity}
 * after the username uniqueness check passes.
 */
public record RegisterRequest(

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 20, message = "Username must be 3–20 characters")
        String username, //the chosen login name; must be between 3 and 20 characters and non-blank

        @NotBlank(message = "Password is required")
        @Size(min = 6, message = "Password must be at least 6 characters")
        String password, //the plain-text password; hashed by UserService before persistence, must be at least 6 characters

        @Email(message = "Invalid email format")
        @NotBlank(message = "Email is required")
        String email //the user's email address; must be non-blank and match standard email format

) {}