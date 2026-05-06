package org.scriptdojo.backend.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO carrying the complete output of a parse run performed by
 * {@link org.scriptdojo.backend.parser.ParserService#parseJavaCode}.
 * Aggregates all artefacts produced during parsing — the success flag,
 * any syntax errors, the constructed AST, and code metrics — into a single
 * object returned to both the HTTP analysis endpoint and the real-time
 * collaboration pipeline.
 * The errors list is initialised to an empty ArrayList so that callers can
 * safely iterate or append to it without null checks.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParseResult {

    /**
     * True if parsing completed without any syntax errors, false otherwise.
     * Set by ParserService based on whether the {@link org.scriptdojo.backend.parser.ErrorListener}
     * collected any errors during the parse run.
     */
    private boolean success;

    /**
     * The list of syntax errors encountered during parsing.
     * Populated by ParserService from the ErrorListener after parsing completes.
     * Initialised to an empty list — never null — so that leaf consumers
     * (e.g. ParserErrorBroadcast construction) can safely call size() and addAll().
     */
    private List<SyntaxError> errors = new ArrayList<>();

    /**
     * The root node of the Abstract Syntax Tree constructed by
     * {@link org.scriptdojo.backend.parser.ASTVisitor} after parsing.
     * Null if parsing failed before the AST could be built.
     */
    private ASTNode ast;

    /**
     * Code quality and structure metrics calculated from the source and AST
     * Null if parsing failed before metrics could be computed.
     */
    private CodeMetrics metrics;

    /** The total time taken to complete the parse run, in milliseconds. */
    private long parseTimeMs;

    /**
     * Appends a single {@link SyntaxError} to the errors list.
     * Used by ParserService to add synthetic error entries when an unexpected
     * exception occurs during parsing, ensuring the result always contains
     * at least one error entry when success is false.
     *
     * @param error the syntax error to add
     */
    public void addError(SyntaxError error) {
        errors.add(error);
    }
}