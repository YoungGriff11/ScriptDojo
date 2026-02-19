package org.scriptdojo.backend.parser;

import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.*;
import org.scriptdojo.backend.service.dto.*;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class ParserService {

    /**
     * Parse Java code and return complete analysis
     */
    public ParseResult parseJavaCode(String code) {
        long startTime = System.currentTimeMillis();

        log.info("════════════════════════════════════════════════════");
        log.info("📝 PARSING JAVA CODE");
        log.info("   Code length: {} characters", code.length());

        ParseResult result = new ParseResult();

        try {
            // Create ANTLR lexer
            CharStream input = CharStreams.fromString(code);
            Java9Lexer lexer = new Java9Lexer(input);

            // Create error listener
            ErrorListener errorListener = new ErrorListener();
            lexer.removeErrorListeners();
            lexer.addErrorListener(errorListener);

            // Create token stream
            CommonTokenStream tokens = new CommonTokenStream(lexer);

            // Create parser
            Java9Parser parser = new Java9Parser(tokens);
            parser.removeErrorListeners();
            parser.addErrorListener(errorListener);

            // Parse the code
            Java9Parser.CompilationUnitContext tree = parser.compilationUnit();

            // Check for errors
            if (errorListener.hasErrors()) {
                log.warn("⚠️ Found {} syntax errors", errorListener.getErrors().size());
                result.setSuccess(false);
                result.getErrors().addAll(errorListener.getErrors());
            } else {
                log.info("✅ No syntax errors found");
                result.setSuccess(true);
            }

            // Build AST
            log.info("🌳 Building AST...");
            ASTVisitor visitor = new ASTVisitor();
            ASTNode ast = visitor.visit(tree);
            result.setAst(ast);
            log.info("✅ AST built with {} children", ast != null ? ast.getChildren().size() : 0);

            // Calculate metrics
            log.info("📊 Calculating metrics...");
            CodeMetrics metrics = calculateMetrics(code, ast);
            result.setMetrics(metrics);
            log.info("✅ Metrics calculated");

        } catch (Exception e) {
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
     * Calculate code metrics
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
                continue;
            } else if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
                commentLines++;
            } else {
                codeLines++;
            }
        }

        metrics.setCodeLines(codeLines);
        metrics.setCommentLines(commentLines);

        // Count AST nodes
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
     * Helper class to count AST nodes
     */
    private static class CountVisitor {
        int classCount = 0;
        int methodCount = 0;
        int fieldCount = 0;

        void count(ASTNode node) {
            if (node == null) return;

            switch (node.getType()) {
                case "ClassDeclaration":
                    classCount++;
                    break;
                case "MethodDeclaration":
                    methodCount++;
                    break;
                case "FieldDeclaration":
                    fieldCount++;
                    break;
            }

            for (ASTNode child : node.getChildren()) {
                count(child);
            }
        }
    }
}