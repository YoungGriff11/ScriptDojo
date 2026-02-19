package org.scriptdojo.backend.parser;

import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.ParseTree;
import org.scriptdojo.backend.service.dto.ASTNode;

@Slf4j
public class ASTVisitor extends Java9BaseVisitor<ASTNode> {

    @Override
    public ASTNode visitCompilationUnit(Java9Parser.CompilationUnitContext ctx) {
        log.info("📝 Visiting compilation unit");

        ASTNode root = new ASTNode();
        root.setType("CompilationUnit");
        root.setName("root");
        root.setStartLine(ctx.getStart().getLine());
        root.setStartColumn(ctx.getStart().getCharPositionInLine());

        if (ctx.getStop() != null) {
            root.setEndLine(ctx.getStop().getLine());
            root.setEndColumn(ctx.getStop().getCharPositionInLine());
        }

        // Visit all children
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

        // Visit class body
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

        // Visit method body
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

    @Override
    protected ASTNode aggregateResult(ASTNode aggregate, ASTNode nextResult) {
        // Return the first non-null result
        return aggregate != null ? aggregate : nextResult;
    }
}