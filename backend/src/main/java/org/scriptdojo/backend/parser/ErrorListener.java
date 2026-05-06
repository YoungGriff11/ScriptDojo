package org.scriptdojo.backend.parser;

import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.*;
import org.scriptdojo.backend.service.dto.SyntaxError;

import java.util.ArrayList;
import java.util.List;

/**
 * Custom ANTLR error listener that collects syntax errors encountered during
 * parsing into a list of {@link SyntaxError} DTOs.
 * Replaces ANTLR's default error listener (which prints to stderr) so that
 * errors can be captured, structured, and broadcast to connected clients
 * via the WebSocket error channel in {@link org.scriptdojo.backend.parser.ParserService}.
 * A new instance is created for each parse invocation so the error list is
 * always scoped to a single parse run and never shared between requests.
 */
@Slf4j
public class ErrorListener extends BaseErrorListener {

    /**
     * Accumulates all syntax errors encountered during a single parse run.
     * Populated by {@link #syntaxError} and read after parsing completes
     * via {@link #getErrors()}.
     */
    private final List<SyntaxError> errors = new ArrayList<>();

    /**
     * Called by the ANTLR runtime whenever a syntax error is encountered.
     * Constructs a {@link SyntaxError} from the error metadata and adds it
     * to the errors list. If the offending symbol is a {@link Token}, its
     * start and end character offsets are also recorded so the frontend can
     * highlight the exact range in the Monaco Editor.
     * @param recognizer          the lexer or parser that encountered the error
     * @param offendingSymbol     the token that caused the error, or null if unavailable
     * @param line                the 1-based line number where the error occurred
     * @param charPositionInLine  the 0-based column offset within the line
     * @param msg                 the human-readable error message produced by ANTLR
     * @param e                   the recognition exception, or null for non-exception errors
     */
    @Override
    public void syntaxError(
            Recognizer<?, ?> recognizer,
            Object offendingSymbol,
            int line,
            int charPositionInLine,
            String msg,
            RecognitionException e
    ) {
        log.warn("🔴 Syntax error at {}:{} - {}", line, charPositionInLine, msg);

        SyntaxError error = new SyntaxError();
        error.setLine(line);
        error.setColumn(charPositionInLine);
        error.setMessage(msg);
        error.setSeverity("ERROR");

        // Token offsets allow the frontend to underline the exact offending
        // token in the Monaco Editor rather than just marking the line
        if (offendingSymbol instanceof Token) {
            Token token = (Token) offendingSymbol;
            error.setStartOffset(token.getStartIndex());
            error.setEndOffset(token.getStopIndex());
        }

        errors.add(error);
    }

    /**
     * Returns all syntax errors collected during the parse run.
     * Called by {@link ParserService} after parsing completes to assemble
     * the {@link org.scriptdojo.backend.service.dto.ParseResult}.
     */
    public List<SyntaxError> getErrors() {
        return errors;
    }

    /**
     * Returns true if at least one syntax error was encountered during parsing.
     * Convenience method used by ParserService to set the success flag on
     * the ParseResult without inspecting the errors list directly.
     */
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}