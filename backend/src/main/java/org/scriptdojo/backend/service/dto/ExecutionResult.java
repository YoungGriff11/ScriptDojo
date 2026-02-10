package org.scriptdojo.backend.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionResult {

    /**
     * Whether execution was successful
     */
    private boolean success;

    /**
     * Program output (stdout)
     */
    private String output;

    /**
     * Error output (stderr)
     */
    private String error;

    /**
     * Execution time in milliseconds
     */
    private long executionTimeMs;

    /**
     * Exit code (0 = success)
     */
    private int exitCode;

    /**
     * Exception message (if execution failed)
     */
    private String exceptionMessage;
}