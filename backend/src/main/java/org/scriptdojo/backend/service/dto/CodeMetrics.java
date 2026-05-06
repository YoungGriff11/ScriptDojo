package org.scriptdojo.backend.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO carrying code quality and structure metrics calculated by
 * a successful parse.
 * Included in the {@link ParseResult} returned to both HTTP analysis requests
 * via {@link org.scriptdojo.backend.controller.ParserController} and real-time
 * edit responses via {@link org.scriptdojo.backend.controller.CollaborationController}.
 */
@Data           // Lombok: generates getters, setters, equals, hashCode, and toString
@NoArgsConstructor
@AllArgsConstructor
public class CodeMetrics {

    /** Total number of lines in the source file, including blank and comment lines. */
    private int totalLines;

    /** Number of non-blank, non-comment lines — the active lines of executable code. */
    private int codeLines;

    /** Number of lines identified as comments (starting with //, /*, or *). */
    private int commentLines;

    /** Number of class declarations found in the AST. */
    private int classCount;

    /** Number of method declarations found in the AST. */
    private int methodCount;

    /** Number of field declarations found in the AST. */
    private int fieldCount;

    /**
     * Cyclomatic complexity of the source code — a measure of the number of
     * linearly independent paths through the code. Higher values indicate
     * greater complexity and potential maintainability concerns.
     * Currently populated as a placeholder; full computation is not yet
     * implemented in ParserService.
     */
    private int cyclomaticComplexity;

    /** Length in lines of the longest method found in the source file. */
    private int maxMethodLength;

    /** Average length in lines across all methods found in the source file. */
    private int avgMethodLength;
}