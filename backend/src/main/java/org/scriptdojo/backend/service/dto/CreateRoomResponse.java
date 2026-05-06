package org.scriptdojo.backend.service.dto;

/**
 * Immutable response record returned by POST /api/room/create after a
 * collaboration room has been successfully created.
 * Carries the generated room ID and the full shareable URL that the host
 * can distribute to guests so they can join the session.
 * @param roomId the generated 11-character alphanumeric room identifier
 * @param url    the full shareable URL guests should navigate to (e.g. http://localhost:8080/room/abc123xyz89)
 */
public record CreateRoomResponse(String roomId, String url) {
}