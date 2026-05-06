package org.scriptdojo.backend.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing a single error or warning produced by the Java compiler
 * during a compilation attempt in {@link org.scriptdojo.backend.service.CompilationService}.
 * Collected into a list on {@link CompilationResult} and broadcast to all room
 * participants via the compiler WebSocket channel when compilation fails, so
 * the output panel can display structured error details rather than raw compiler output.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompilationError {

    /** The 1-based line number in the source file where the error occurred. */
    private long lineNumber;

    /** The 1-based column number within the line where the error occurred. */
    private long columnNumber;

    /** The human-readable error message produced by the Java compiler. */
    private String message;

    /**
     * The severity category assigned by the compiler.
     * Typically "ERROR" for fatal issues or "WARNING" for non-fatal diagnostics.
     */
    private String kind;

    /**
     * The source code snippet surrounding the error location.
     * Displayed in the output panel to give participants immediate context
     * without needing to locate the error in the editor manually.
     */
    private String code;
}