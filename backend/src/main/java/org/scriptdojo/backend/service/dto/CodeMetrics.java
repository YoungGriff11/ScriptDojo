package org.scriptdojo.backend.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CodeMetrics {
    private int totalLines;
    private int codeLines;          // Non-blank, non-comment lines
    private int commentLines;
    private int classCount;
    private int methodCount;
    private int fieldCount;
    private int cyclomaticComplexity;  // Measure of code complexity
    private int maxMethodLength;
    private int avgMethodLength;
}