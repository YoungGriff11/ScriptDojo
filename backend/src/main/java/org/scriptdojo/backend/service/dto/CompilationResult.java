package org.scriptdojo.backend.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO carrying the outcome of a compilation attempt performed by
 * {@link org.scriptdojo.backend.service.CompilationService}.
 * Returned to {@link org.scriptdojo.backend.controller.CompilerController},
 * which uses it to determine whether to proceed to execution and to construct
 * the WebSocket broadcast payload sent to all room participants.
 * Also included in the HTTP response body of POST /api/compiler/run so the
 * triggering client can inspect the full compilation outcome if needed.
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompilationResult {

    /**
     * True if compilation completed without errors, false otherwise.
     * The primary flag used by CompilerController to decide whether to
     * proceed to the execution stage of the pipeline.
     */
    private boolean success;

    /**
     * The list of structured errors and warnings produced by the compiler.
     * Populated when success is false; empty or null when compilation succeeds.
     * Broadcast to the room via the compiler WebSocket channel so all participants
     * can see the specific errors in their output panel.
     */
    private List<CompilationError> errors;

    /**
     * The name of the compiled class, extracted from the source before compilation.
     * Used by {@link org.scriptdojo.backend.service.ExecutionService} to identify
     * which class to invoke when running the compiled bytecode.
     * Null if compilation failed before the class name could be determined.
     */
    private String className;

    /** The time taken to compile the source code, in milliseconds. */
    private long compilationTimeMs;

    /**
     * A high-level error message for critical failures that prevented compilation
     * from producing structured error diagnostics (e.g. an I/O exception writing
     * the source file). Null when structured errors are available via {@link #errors}.
     */
    private String errorMessage;

    /**
     * The filesystem path of the directory where the compiled .class file was written.
     * Passed to {@link org.scriptdojo.backend.service.ExecutionService} to locate
     * the bytecode for execution. Null if compilation failed.
     */
    private String outputDirectory;
}