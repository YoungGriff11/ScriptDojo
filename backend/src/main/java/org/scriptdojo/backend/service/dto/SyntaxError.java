package org.scriptdojo.backend.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SyntaxError {
    private int line;
    private int column;
    private String message;
    private String severity; // "ERROR", "WARNING", "INFO"
    private int startOffset;
    private int endOffset;
}