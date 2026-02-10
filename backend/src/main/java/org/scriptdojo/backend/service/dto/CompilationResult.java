package org.scriptdojo.backend.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompilationResult {

    /**
     * Whether compilation was successful
     */
    private boolean success;

    /**
     * List of compilation errors (if any)
     */
    private List<CompilationError> errors;

    /**
     * Compiled class name (if successful)
     */
    private String className;

    /**
     * Compilation time in milliseconds
     */
    private long compilationTimeMs;

    /**
     * Error message for critical failures
     */
    private String errorMessage;

    /**
     * Output directory where .class file was saved
     */
    private String outputDirectory;
}