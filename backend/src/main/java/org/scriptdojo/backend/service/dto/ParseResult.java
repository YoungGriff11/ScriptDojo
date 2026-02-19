package org.scriptdojo.backend.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParseResult {
    private boolean success;
    private List<SyntaxError> errors = new ArrayList<>();
    private ASTNode ast;
    private CodeMetrics metrics;
    private long parseTimeMs;

    public void addError(SyntaxError error) {
        errors.add(error);
    }
}