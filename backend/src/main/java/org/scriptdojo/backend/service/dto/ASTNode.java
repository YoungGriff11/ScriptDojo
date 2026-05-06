package org.scriptdojo.backend.service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO representing a single node in the Abstract Syntax Tree (AST) produced
 * by {@link org.scriptdojo.backend.parser.ASTVisitor} after a successful parse.
 * Nodes are arranged in a tree structure mirroring the hierarchical structure
 * of the Java source code — a CompilationUnit contains ClassDeclarations, which
 * contain MethodDeclarations and FieldDeclarations, and so on.
 * Included in the {@link ParseResult} returned by
 * {@link org.scriptdojo.backend.parser.ParserService} for both HTTP analysis
 * requests and real-time collaborative edit responses.
 */
@Data           // Lombok: generates getters, setters, equals, hashCode, and toString
@NoArgsConstructor
@AllArgsConstructor
public class ASTNode {

    /** The grammatical category of this node (e.g. "ClassDeclaration", "MethodDeclaration", "FieldDeclaration"). */
    private String type;

    /** The identifier associated with this node (e.g. the class name, method name, or field name). */
    private String name;

    /** The 1-based line number in the source file where this node begins. */
    private int startLine;

    /** The 1-based line number in the source file where this node ends. */
    private int endLine;

    /** The 0-based column offset within the start line where this node begins. */
    private int startColumn;

    /** The 0-based column offset within the end line where this node ends. */
    private int endColumn;

    /**
     * The child nodes of this node in the AST hierarchy.
     * Initialised to an empty list so that leaf nodes (e.g. FieldDeclarations)
     * can be safely iterated without null checks.
     */
    private List<ASTNode> children = new ArrayList<>();

    /**
     * Appends a child node to this node's children list.
     * Called by {@link org.scriptdojo.backend.parser.ASTVisitor} during tree
     * construction to attach parsed sub-nodes to their parent.
     * @param child the child ASTNode to add
     */
    public void addChild(ASTNode child) {
        children.add(child);
    }
}