package org.scriptdojo.backend.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO carrying the outcome of a bytecode execution attempt performed by
 * {@link org.scriptdojo.backend.service.ExecutionService}.
 * Returned to {@link org.scriptdojo.backend.controller.CompilerController}
 * after the compilation stage succeeds, and included in both the WebSocket
 * broadcast to all room participants and the HTTP response body of
 * POST /api/compiler/run.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResult {

    /**
     * True if the program exited with code 0 and produced no runtime errors,
     * false otherwise. Used by CompilerController to determine which WebSocket
     * event to broadcast (execution_success or execution_failed).
     */
    private boolean success;

    /**
     * The standard output (stdout) produced by the program during execution.
     * Displayed in the output panel of all room participants on success.
     * May be empty if the program produced no output.
     */
    private String output;

    /**
     * The standard error (stderr) output produced by the program.
     * Populated when the program writes to stderr or terminates abnormally.
     * Null or empty on successful execution.
     */
    private String error;

    /** The time taken to execute the compiled program, in milliseconds. */
    private long executionTimeMs;

    /**
     * The process exit code returned by the JVM subprocess.
     * 0 indicates clean termination; any non-zero value indicates an error
     * or abnormal exit (e.g. uncaught exception, System.exit() with non-zero argument).
     */
    private int exitCode;

    /**
     * The message of any exception that caused execution to fail, if applicable.
     * Distinct from {@link #error} — this captures JVM-level exceptions thrown
     * during subprocess management rather than program stderr output.
     * Null if execution succeeded or failed without a thrown exception.
     */
    private String exceptionMessage;
}