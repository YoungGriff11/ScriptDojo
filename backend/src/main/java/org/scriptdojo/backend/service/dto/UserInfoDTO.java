package org.scriptdojo.backend.service.dto;

/**
 * Immutable record used to transfer basic user profile information in the
 * response to GET /api/user/me.
 * Exposes only the fields the frontend requires for display purposes —
 * sensitive fields such as the password hash and role are deliberately excluded.
 *
 * @param id       the unique database identifier of the authenticated user
 * @param username the login name of the authenticated user
 * @param email    the email address of the authenticated user
 */
public record UserInfoDTO(Long id, String username, String email) {}