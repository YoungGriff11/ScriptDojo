package org.scriptdojo.backend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.scriptdojo.backend.service.CompilationService;
import org.scriptdojo.backend.service.ExecutionService;
import org.scriptdojo.backend.service.dto.CompilationResult;
import org.scriptdojo.backend.service.dto.ExecutionResult;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/compiler")
@RequiredArgsConstructor
@Slf4j
public class CompilerController {

    private final CompilationService compilationService;
    private final ExecutionService executionService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Compile and execute Java code
     * POST /api/compiler/run
     */
    @PostMapping("/run")
    public ResponseEntity<?> compileAndRun(
            @RequestBody Map<String, String> request,
            Authentication auth
    ) {
        String sourceCode = request.get("code");
        Long fileId = Long.parseLong(request.get("fileId"));

        log.info("════════════════════════════════════════════════════");
        log.info("🎯 COMPILE & RUN REQUEST");
        log.info("   File ID: {}", fileId);
        log.info("   User: {}", auth != null ? auth.getName() : "Anonymous");
        log.info("   Code length: {} characters", sourceCode.length());

        // Extract class name from code
        String className = compilationService.extractClassName(sourceCode);
        log.info("   Detected class: {}", className);

        // Broadcast compilation started
        broadcastToRoom(fileId, "compilation_started", Map.of(
                "className", className,
                "timestamp", System.currentTimeMillis()
        ));

        // Step 1: Compile
        CompilationResult compilationResult = compilationService.compile(sourceCode, className);

        if (!compilationResult.isSuccess()) {
            log.warn("❌ Compilation failed");

            // Broadcast compilation errors
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("errors", compilationResult.getErrors());
            errorData.put("errorMessage", compilationResult.getErrorMessage() != null ? compilationResult.getErrorMessage() : "");
            errorData.put("compilationTimeMs", compilationResult.getCompilationTimeMs());

            broadcastToRoom(fileId, "compilation_failed", errorData);

            log.info("════════════════════════════════════════════════════");

            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "stage", "compilation",
                    "compilationResult", compilationResult
            ));
        }

        log.info("✅ Compilation successful");

        // Broadcast compilation success
        broadcastToRoom(fileId, "compilation_success", Map.of(
                "className", className,
                "compilationTimeMs", compilationResult.getCompilationTimeMs()
        ));

        // Step 2: Execute
        Path compiledClassPath = Paths.get(compilationResult.getOutputDirectory());
        log.info("🚀 Executing from: {}", compiledClassPath);
        ExecutionResult executionResult = executionService.execute(className, compiledClassPath);

        // Broadcast execution result
        if (executionResult.isSuccess()) {
            broadcastToRoom(fileId, "execution_success", Map.of(
                    "output", executionResult.getOutput(),
                    "executionTimeMs", executionResult.getExecutionTimeMs()
            ));
        } else {
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("error", executionResult.getError());
            errorData.put("exceptionMessage", executionResult.getExceptionMessage() != null ? executionResult.getExceptionMessage() : "");
            errorData.put("exitCode", executionResult.getExitCode());

            broadcastToRoom(fileId, "execution_failed", errorData);
        }

        log.info("════════════════════════════════════════════════════");

        // Return complete result
        Map<String, Object> response = new HashMap<>();
        response.put("success", executionResult.isSuccess());
        response.put("stage", "execution");
        response.put("compilationResult", compilationResult);
        response.put("executionResult", executionResult);

        return ResponseEntity.ok(response);
    }

    /**
     * Broadcast message to all users in a room
     */
    private void broadcastToRoom(Long fileId, String event, Map<String, Object> data) {
        Map<String, Object> message = new HashMap<>(data);
        message.put("event", event);
        message.put("fileId", fileId);

        messagingTemplate.convertAndSend("/topic/room/" + fileId + "/compiler", message);

        log.debug("📡 Broadcast to room {}: {}", fileId, event);
    }
}