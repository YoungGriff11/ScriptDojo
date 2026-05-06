package org.scriptdojo.backend.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO broadcast to all room subscribers on the /topic/room/{fileId}/errors
 * WebSocket channel after every edit is parsed by
 * {@link org.scriptdojo.backend.parser.ParserService}.
 * Received by the React frontend, which uses the error list to update Monaco
 * Editor's error markers (red squiggles) in real time. An empty error list
 * signals that all existing markers should be cleared.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParserErrorBroadcast {

    /** The display name of the participant whose edit triggered the parse. */
    private String username;

    /**
     * The list of syntax errors found during parsing.
     * Empty if the current editor content is valid or blank — an empty list
     * is broadcast explicitly to clear any previously displayed error markers.
     */
    private List<SyntaxError> errors;

    /**
     * The server-side timestamp (epoch milliseconds) at which the parse completed.
     * Allows the frontend to discard out-of-order broadcasts if multiple rapid
     * edits produce parse results that arrive out of sequence.
     */
    private long timestamp;
}