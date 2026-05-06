package org.scriptdojo.backend.service.dto;

/**
 * Immutable record representing a real-time cursor position update sent by a
 * participant in a collaborative editing session.
 * Received by {@link org.scriptdojo.backend.controller.CollaborationController#handleCursor}
 * and broadcast as-is to all room subscribers so each participant's Monaco Editor
 * can render the cursor positions of others as live overlays.
 * @param username the display name of the participant who moved their cursor
 * @param line     the 1-based line number of the cursor's current position
 * @param column   the 0-based column offset of the cursor's current position
 */
public record CursorMove(String username, int line, int column) {}