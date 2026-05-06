package org.scriptdojo.backend.parser;

import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.*;
import org.scriptdojo.backend.service.dto.*;
import org.springframework.stereotype.Service;

/**
 * Service that orchestrates the ANTLR v4 Java 9 parsing pipeline for ScriptDojo.
 * Accepts raw Java source code and returns a {@link ParseResult} containing
 * any syntax errors, the constructed AST, and basic code metrics.
 * Called by {@link org.scriptdojo.backend.controller.CollaborationController}
 * on every real-time edit to detect syntax errors for broadcast, and by
 * {@link org.scriptdojo.backend.controller.ParserController} for on-demand
 * HTTP analysis requests.
 */
@Service
@Slf4j
public class ParserService {

    /**
     * Parses a Java source code string and returns a complete analysis result.
     * Runs the full ANTLR pipeline — lexing, token streaming, parsing, AST
     * construction, and metric calculation — in a single synchronous call.
     * If an unexpected exception occurs at any stage, a synthetic error entry
     * is added to the result rather than propagating the exception to the caller,
     * keeping parser failures non-critical for the collaboration flow.
     * @param code the raw Java source code to parse
     * @return a {@link ParseResult} containing the success flag, any syntax errors,
     *         the constructed AST, code metrics, and the total parse duration
     */
    public ParseResult parseJavaCode(String code) {
        long startTime = System.currentTimeMillis();

        log.info("════════════════════════════════════════════════════");
        log.info("📝 PARSING JAVA CODE");
        log.info("   Code length: {} characters", code.length());

        ParseResult result = new ParseResult();

        try {
            // ─ Lexing
            // Convert the raw source string into a character stream for the lexer
            CharStream input = CharStreams.fromString(code);
            Java9Lexer lexer = new Java9Lexer(input);

            // Replace ANTLR's default stderr error listener with our custom one
            // so lexer errors are captured as structured SyntaxError objects
            ErrorListener errorListener = new ErrorListener();
            lexer.removeErrorListeners();
            lexer.addErrorListener(errorListener);

            // ─ Token streaming
            CommonTokenStream tokens = new CommonTokenStream(lexer);

            // ─ Parsing
            // Attach the same ErrorListener to the parser so both lexer and
            // parser errors are collected in a single list
            Java9Parser parser = new Java9Parser(tokens);
            parser.removeErrorListeners();
            parser.addErrorListener(errorListener);

            // Entry point for the Java 9 grammar — parses the full source file
            Java9Parser.CompilationUnitContext tree = parser.compilationUnit();

            // ─ Error evaluation
            if (errorListener.hasErrors()) {
                log.warn("⚠️ Found {} syntax errors", errorListener.getErrors().size());
                result.setSuccess(false);
                result.getErrors().addAll(errorListener.getErrors());
            } else {
                log.info("✅ No syntax errors found");
                result.setSuccess(true);
            }

            // ─ AST construction
            // Walk the ANTLR parse tree and convert it into ScriptDojo ASTNodes
            log.info("🌳 Building AST...");
            ASTVisitor visitor = new ASTVisitor();
            ASTNode ast = visitor.visit(tree);
            result.setAst(ast);
            log.info("✅ AST built with {} children", ast != null ? ast.getChildren().size() : 0);

            // ─ Metrics
            log.info("📊 Calculating metrics...");
            CodeMetrics metrics = calculateMetrics(code, ast);
            result.setMetrics(metrics);
            log.info("✅ Metrics calculated");

        } catch (Exception e) {
            // Any unexpected parser exception is caught and surfaced as a synthetic
            // error entry so the caller always receives a valid ParseResult
            log.error("❌ Parsing failed", e);
            result.setSuccess(false);
            SyntaxError error = new SyntaxError();
            error.setLine(0);
            error.setColumn(0);
            error.setMessage("Parser exception: " + e.getMessage());
            error.setSeverity("ERROR");
            result.addError(error);
        }

        long parseTime = System.currentTimeMillis() - startTime;
        result.setParseTimeMs(parseTime);

        log.info("⏱️ Parsing completed in {}ms", parseTime);
        log.info("════════════════════════════════════════════════════");

        return result;
    }

    /**
     * Calculates basic code metrics from the source string and the constructed AST.
     * Line counts are derived by splitting the raw source on newlines and classifying
     * each non-empty line as a comment or code line. Class, method, and field counts
     * are obtained by walking the AST with {@link CountVisitor}.
     * @param code the raw Java source code
     * @param ast  the root ASTNode produced by {@link ASTVisitor}, or null if parsing failed
     * @return a {@link CodeMetrics} object populated with line and declaration counts
     */
    private CodeMetrics calculateMetrics(String code, ASTNode ast) {
        CodeMetrics metrics = new CodeMetrics();

        String[] lines = code.split("\n");
        metrics.setTotalLines(lines.length);

        int codeLines = 0;
        int commentLines = 0;

        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue; // Blank lines are excluded from both counts
            } else if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
                commentLines++;
            } else {
                codeLines++;
            }
        }

        metrics.setCodeLines(codeLines);
        metrics.setCommentLines(commentLines);

        // Walk the AST to count class, method, and field declarations
        if (ast != null) {
            CountVisitor counter = new CountVisitor();
            counter.count(ast);
            metrics.setClassCount(counter.classCount);
            metrics.setMethodCount(counter.methodCount);
            metrics.setFieldCount(counter.fieldCount);
        }

        return metrics;
    }

    /**
     * Simple recursive AST walker that counts declaration nodes by type.
     * Traverses the full AST depth-first, incrementing the relevant counter
     * for each ClassDeclaration, MethodDeclaration, or FieldDeclaration node.
     */
    private static class CountVisitor {
        int classCount = 0;
        int methodCount = 0;
        int fieldCount = 0;

        /**
         * Recursively walks the AST rooted at the given node, counting
         * declaration nodes by their type string.
         * @param node the current AST node to inspect; null nodes are silently skipped
         */
        void count(ASTNode node) {
            if (node == null) return;

            switch (node.getType()) {
                case "ClassDeclaration":  classCount++;  break;
                case "MethodDeclaration": methodCount++; break;
                case "FieldDeclaration":  fieldCount++;  break;
            }

            // Recurse into children regardless of the current node's type
            for (ASTNode child : node.getChildren()) {
                count(child);
            }
        }
    }
}