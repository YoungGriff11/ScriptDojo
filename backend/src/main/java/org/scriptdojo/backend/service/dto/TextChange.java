package org.scriptdojo.backend.service.dto;

/**
 * Immutable record representing a real-time text edit sent by a participant
 * in a collaborative editing session.
 * Received by {@link org.scriptdojo.backend.controller.CollaborationController#handleEdit}
 * via the /app/room/{fileId}/edit STOMP destination and broadcast to all room
 * subscribers on /topic/room/{fileId} so their Monaco Editor instances stay
 * in sync with the latest content.
 * The full editor content is transmitted on every edit rather than a delta,
 * simplifying conflict resolution at the cost of payload size.
 *
 * @param content  the complete current content of the editor at the time of the edit
 * @param username the display name of the participant who made the edit;
 *                 supplied in the message body by guests since they have no
 *                 Spring Security Principal, and resolved from the Principal
 *                 for authenticated hosts
 */
public record TextChange(String content, String username) {
}