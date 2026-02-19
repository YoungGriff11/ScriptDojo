package org.scriptdojo.backend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.scriptdojo.backend.parser.ParserService;
import org.scriptdojo.backend.service.dto.ParseResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/parser")
@RequiredArgsConstructor
@Slf4j
public class ParserController {

    private final ParserService parserService;

    /**
     * Parse Java code and return AST + errors + metrics
     * POST /api/parser/analyze
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