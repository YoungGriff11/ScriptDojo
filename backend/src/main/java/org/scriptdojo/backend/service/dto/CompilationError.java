package org.scriptdojo.backend.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompilationError {

    /**
     * Line number where error occurred
     */
    private long lineNumber;

    /**
     * Column number where error occurred
     */
    private long columnNumber;

    /**
     * Error message from compiler
     */
    private String message;

    /**
     * Error kind (ERROR, WARNING, etc.)
     */
    private String kind;

    /**
     * Source code snippet around error
     */
    private String code;
}