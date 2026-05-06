package org.scriptdojo.backend.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a single syntax error detected by the ANTLR parser during
 * a parse run in {@link org.scriptdojo.backend.parser.ParserService}.
 * Populated by {@link org.scriptdojo.backend.parser.ErrorListener} and collected
 * into a list on {@link ParseResult}. Broadcast to all room participants via
 * {@link ParserErrorBroadcast} so the React frontend can create Monaco Editor
 * markers at the exact error location in real time.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyntaxError {

    /** The 1-based line number in the source file where the error was detected. */
    private int line;

    /** The 0-based column offset within the line where the error was detected. */
    private int column;

    /** The human-readable error message produced by the ANTLR parser. */
    private String message;

    /**
     * The severity level of this error — "ERROR", "WARNING", or "INFO".
     * Mapped to the corresponding Monaco Editor marker severity so the frontend
     * can render the appropriate visual indicator (red, yellow, or blue squiggle).
     */
    private String severity;

    /**
     * The absolute character offset in the source string where the offending
     * token begins. Used alongside {@link #endOffset} by Monaco Editor to
     * highlight the exact token range rather than just the line.
     */
    private int startOffset;

    /**
     * The absolute character offset in the source string where the offending
     * token ends. Null-equivalent (0) if the offending symbol was not a
     * {@link org.antlr.v4.runtime.Token} and offsets could not be determined.
     */
    private int endOffset;
}