package org.scriptdojo.backend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.scriptdojo.backend.parser.ParserService;
import org.scriptdojo.backend.service.dto.ParseResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller that exposes the ANTLR v4 Java parser as an HTTP endpoint.
 *
 * Primarily intended for development and debugging — allows direct inspection
 * of the parse result (errors, AST structure, and metrics) for a given snippet
 * of Java source code without needing an active collaborative editing session.
 *
 * In normal application flow, parsing is triggered automatically by
 * CollaborationController on every real-time edit and the results are broadcast
 * over WebSocket rather than returned via HTTP. This endpoint provides a
 * synchronous alternative for tooling, testing, or diagnostics.
 *
 * Base path: /api/parser
 */
@RestController
@RequestMapping("/api/parser")
@RequiredArgsConstructor // Lombok: generates constructor injecting parserService
@Slf4j                   // Lombok: injects a static SLF4J logger
public class ParserController {

    // Runs the ANTLR v4 Java grammar against source code and returns a
    // structured result containing any syntax errors, AST data, and metrics
    private final ParserService parserService;

    /**
     * Parses a Java source code string and returns the full parse result.
     *
     * Delegates directly to {@link ParserService#parseJavaCode(String)}, which
     * runs the ANTLR v4 Java grammar and collects any syntax errors encountered
     * during the parse. The result includes a success flag, the list of errors
     * (empty if the code is valid), and any additional parse metrics.
     *
     * POST /api/parser/analyze
     *
     * @param code the raw Java source code to parse, supplied as the request body
     * @return 200 OK with a {@link ParseResult} containing:
     *         - success: true if no syntax errors were found
     *         - errors:  list of syntax errors with line/column positions
     *         - any additional AST or metric fields populated by ParserService
     */
    @PostMapping("/analyze")
    public ResponseEntity<ParseResult> analyzeCode(@RequestBody String code) {
        log.info("📡 API: Parse request received");
        log.info("   Code length: {} characters", code.length());

        ParseResult result = parserService.parseJavaCode(code);

        log.info("✅ Parse result: success={}, errors={}",
                result.isSuccess(), result.getErrors().size());

        return ResponseEntity.ok(result);
    }
}