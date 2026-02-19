package org.scriptdojo.backend.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ASTNode {
    private String type;        // "ClassDeclaration", "MethodDeclaration", etc.
    private String name;        // Name of class/method/variable
    private int startLine;
    private int endLine;
    private int startColumn;
    private int endColumn;
    private List<ASTNode> children = new ArrayList<>();

    public void addChild(ASTNode child) {
        children.add(child);
    }
}