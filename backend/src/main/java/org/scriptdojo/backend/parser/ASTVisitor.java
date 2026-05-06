package org.scriptdojo.backend.parser;

import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.scriptdojo.backend.service.dto.ASTNode;

/**
 * ANTLR v4 visitor that traverses the Java 9 parse tree and converts it into
 * a tree of {@link ASTNode} objects for use in the ScriptDojo parser pipeline.
 * Only the node types relevant to ScriptDojo are visited (compilation unit,
 * class declarations, method declarations, and field declarations) — all other
 * parse tree nodes are ignored via the default visitor behaviour.
 * Extends Java9BaseVisitor so that only the overridden visit methods produce
 * ASTNode output; unhandled nodes return null and are filtered out by their
 * parent's child-collection loop.
 */
@Slf4j
public class ASTVisitor extends Java9BaseVisitor<ASTNode> {

    /**
     * Visits the root compilation unit and builds the top-level ASTNode.
     * Iterates over all direct children of the compilation unit and recursively
     * visits any that are parser rule contexts (i.e. non-terminal nodes),
     * attaching the resulting child nodes to the root.
     * @param ctx the ANTLR parse tree context for the compilation unit
     * @return the root ASTNode representing the entire source file
     */
    @Override
    public ASTNode visitCompilationUnit(Java9Parser.CompilationUnitContext ctx) {
        log.info("📝 Visiting compilation unit");

        ASTNode root = new ASTNode();
        root.setType("CompilationUnit");
        root.setName("root");
        root.setStartLine(ctx.getStart().getLine());
        root.setStartColumn(ctx.getStart().getCharPositionInLine());

        // getStop() may be null for empty or incomplete source files
        if (ctx.getStop() != null) {
            root.setEndLine(ctx.getStop().getLine());
            root.setEndColumn(ctx.getStop().getCharPositionInLine());
        }

        // Only visit parser rule contexts — terminal nodes (tokens) are skipped
        // as they do not produce meaningful ASTNode entries at this level
        for (ParseTree child : ctx.children) {
            if (child instanceof ParserRuleContext) {
                ASTNode childNode = visit(child);
                if (childNode != null) {
                    root.addChild(childNode);
                }
            }
        }

        return root;
    }

    /**
     * Visits a class declaration and builds an ASTNode representing it.
     * Iterates over the class body declarations and recursively visits each,
     * attaching method and field nodes as children of the class node.
     * @param ctx the ANTLR parse tree context for the class declaration
     * @return an ASTNode of type "ClassDeclaration" with its body as children
     */
    @Override
    public ASTNode visitClassDeclaration(Java9Parser.ClassDeclarationContext ctx) {
        log.info("📦 Visiting class: {}", ctx.IDENTIFIER().getText());

        ASTNode node = new ASTNode();
        node.setType("ClassDeclaration");
        node.setName(ctx.IDENTIFIER().getText());
        node.setStartLine(ctx.getStart().getLine());
        node.setStartColumn(ctx.getStart().getCharPositionInLine());

        if (ctx.getStop() != null) {
            node.setEndLine(ctx.getStop().getLine());
            node.setEndColumn(ctx.getStop().getCharPositionInLine());
        }

        // Visit each declaration in the class body (methods, fields, nested classes)
        if (ctx.classBody() != null) {
            for (Java9Parser.ClassBodyDeclarationContext bodyCtx : ctx.classBody().classBodyDeclaration()) {
                ASTNode childNode = visit(bodyCtx);
                if (childNode != null) {
                    node.addChild(childNode);
                }
            }
        }

        return node;
    }

    /**
     * Visits a method declaration and builds an ASTNode representing it.
     * Iterates over the statements in the method body and attaches them
     * as children of the method node.
     * @param ctx the ANTLR parse tree context for the method declaration
     * @return an ASTNode of type "MethodDeclaration" with its body statements as children
     */
    @Override
    public ASTNode visitMethodDeclaration(Java9Parser.MethodDeclarationContext ctx) {
        log.info("⚙️ Visiting method: {}", ctx.IDENTIFIER().getText());

        ASTNode node = new ASTNode();
        node.setType("MethodDeclaration");
        node.setName(ctx.IDENTIFIER().getText());
        node.setStartLine(ctx.getStart().getLine());
        node.setStartColumn(ctx.getStart().getCharPositionInLine());

        if (ctx.getStop() != null) {
            node.setEndLine(ctx.getStop().getLine());
            node.setEndColumn(ctx.getStop().getCharPositionInLine());
        }

        // Visit each statement in the method body; null checks guard against
        // abstract methods or methods with empty bodies
        if (ctx.methodBody() != null && ctx.methodBody().block() != null) {
            for (Java9Parser.BlockStatementContext stmtCtx : ctx.methodBody().block().blockStatement()) {
                ASTNode childNode = visit(stmtCtx);
                if (childNode != null) {
                    node.addChild(childNode);
                }
            }
        }

        return node;
    }

    /**
     * Visits a field declaration and builds a leaf ASTNode representing it.
     * Fields have no child nodes in the ScriptDojo AST representation —
     * only their position and name are recorded.
     * @param ctx the ANTLR parse tree context for the field declaration
     * @return a leaf ASTNode of type "FieldDeclaration"
     */
    @Override
    public ASTNode visitFieldDeclaration(Java9Parser.FieldDeclarationContext ctx) {
        log.info("📌 Visiting field: {}", ctx.IDENTIFIER().getText());

        ASTNode node = new ASTNode();
        node.setType("FieldDeclaration");
        node.setName(ctx.IDENTIFIER().getText());
        node.setStartLine(ctx.getStart().getLine());
        node.setStartColumn(ctx.getStart().getCharPositionInLine());

        if (ctx.getStop() != null) {
            node.setEndLine(ctx.getStop().getLine());
            node.setEndColumn(ctx.getStop().getCharPositionInLine());
        }

        return node;
    }

    /**
     * Overrides the default ANTLR result aggregation strategy.
     * Returns the first non-null result encountered during child visitation,
     * discarding subsequent results. This ensures that visitor methods which
     * do not explicitly iterate children still propagate a meaningful result
     * up the parse tree rather than losing it to the default null aggregation.
     * @param aggregate  the result accumulated so far
     * @param nextResult the result from the next child
     * @return aggregate if non-null, otherwise nextResult
     */
    @Override
    protected ASTNode aggregateResult(ASTNode aggregate, ASTNode nextResult) {
        return aggregate != null ? aggregate : nextResult;
    }
}