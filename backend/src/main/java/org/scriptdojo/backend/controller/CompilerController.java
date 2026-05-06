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

/**
 * REST controller that compiles and executes Java source code submitted from
 * the ScriptDojo collaborative editor.
 * Exposes a single endpoint (POST /api/compiler/run) that orchestrates a
 * two-stage pipeline:
 *   1. Compilation — source code is compiled via {@link CompilationService}
 *      using the javax.tools.JavaCompiler API
 *   2. Execution   — the compiled bytecode is run in an isolated subprocess
 *      via {@link ExecutionService}
 * Each stage broadcasts its outcome to the room's compiler WebSocket channel
 * (/topic/room/{fileId}/compiler) so all participants see live feedback in
 * their output panel regardless of who triggered the run.
 * The pipeline short-circuits after compilation if errors are found — execution
 * is only attempted when compilation succeeds.
 */
@RestController
@RequestMapping("/api/compiler")
@RequiredArgsConstructor // Lombok: generates constructor injecting all final fields
@Slf4j                   // Lombok: injects a static SLF4J logger
public class CompilerController {

    // Handles source code compilation using the javax.tools.JavaCompiler API;
    // also responsible for extracting the public class name from the source
    private final CompilationService compilationService;

    // Runs the compiled bytecode in an isolated subprocess and captures stdout/stderr
    private final ExecutionService executionService;

    // Used to push compiler stage events to all room subscribers over WebSocket
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Compiles and executes Java source code submitted from the editor.
     * Orchestrates the full compile → execute pipeline and broadcasts the outcome
     * of each stage to all participants in the room via the compiler WebSocket channel.
     * The HTTP response always returns 200 OK; success or failure is communicated
     * through the response body and the WebSocket broadcasts, not the HTTP status code.
     * Pipeline:
     *   compilation_started  → broadcast immediately so the UI can show a loading state
     *   compilation_failed   → broadcast + return early if the source has errors
     *   compilation_success  → broadcast, then proceed to execution
     *   execution_success /
     *   execution_failed     → broadcast the final outcome with output or error details
     * POST /api/compiler/run
     * @param request JSON body containing:
     *                "code"   — the full Java source code to compile and run
     *                "fileId" — the room/file ID used to route WebSocket broadcasts
     * @param auth    the Spring Security authentication of the requesting user,
     *                or null if the request originates from an unauthenticated guest
     * @return 200 OK with a JSON body describing the outcome of the pipeline;
     *         includes compilationResult always, and executionResult when compilation succeeds
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

        // Extract the public class name from the source so the compiler knows what
        // filename to write and the executor knows what class to invoke
        String className = compilationService.extractClassName(sourceCode);
        log.info("   Detected class: {}", className);

        // ─ Stage 0: Notify room that compilation is starting
        // Broadcast immediately so all participants' output panels can show a
        // loading/spinner state before results arrive
        broadcastToRoom(fileId, "compilation_started", Map.of(
                "className", className,
                "timestamp", System.currentTimeMillis()
        ));

        // ─ Stage 1: Compile
        CompilationResult compilationResult = compilationService.compile(sourceCode, className);

        if (!compilationResult.isSuccess()) {
            log.warn("❌ Compilation failed");

            // Broadcast the compiler errors so all participants see them in the output panel.
            // errorMessage may be null if the failure produced structured error objects only,
            // so it is defaulted to an empty string to keep the payload JSON-safe.
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("errors", compilationResult.getErrors());
            errorData.put("errorMessage", compilationResult.getErrorMessage() != null
                    ? compilationResult.getErrorMessage() : "");
            errorData.put("compilationTimeMs", compilationResult.getCompilationTimeMs());

            broadcastToRoom(fileId, "compilation_failed", errorData);

            log.info("════════════════════════════════════════════════════");

            // Short-circuit: return the compilation result without attempting execution
            return ResponseEntity.ok(Map.of(
                    "success", false,
                    "stage", "compilation",   // Tells the client which stage failed
                    "compilationResult", compilationResult
            ));
        }

        log.info("✅ Compilation successful");

        // Broadcast compilation success so the UI can update before execution begins
        broadcastToRoom(fileId, "compilation_success", Map.of(
                "className", className,
                "compilationTimeMs", compilationResult.getCompilationTimeMs()
        ));

        // ─ Stage 2: Execute
        // Resolve the directory where the compiler wrote the .class file, then
        // hand it to ExecutionService to run in an isolated subprocess
        Path compiledClassPath = Paths.get(compilationResult.getOutputDirectory());
        log.info("🚀 Executing from: {}", compiledClassPath);
        ExecutionResult executionResult = executionService.execute(className, compiledClassPath);

        // Broadcast the execution outcome to the room.
        // exceptionMessage may be null on clean runtime failures (e.g. non-zero exit code
        // with no thrown exception), so it is defaulted to an empty string.
        if (executionResult.isSuccess()) {
            broadcastToRoom(fileId, "execution_success", Map.of(
                    "output", executionResult.getOutput(),
                    "executionTimeMs", executionResult.getExecutionTimeMs()
            ));
        } else {
            Map<String, Object> errorData = new HashMap<>();
            errorData.put("error", executionResult.getError());
            errorData.put("exceptionMessage", executionResult.getExceptionMessage() != null
                    ? executionResult.getExceptionMessage() : "");
            errorData.put("exitCode", executionResult.getExitCode());

            broadcastToRoom(fileId, "execution_failed", errorData);
        }

        log.info("════════════════════════════════════════════════════");

        // ─ Return complete pipeline result
        // Both stages are included in the HTTP response so the triggering client
        // can inspect the full result if needed, even though all participants
        // already received the outcome via WebSocket broadcast
        Map<String, Object> response = new HashMap<>();
        response.put("success", executionResult.isSuccess());
        response.put("stage", "execution");
        response.put("compilationResult", compilationResult);
        response.put("executionResult", executionResult);

        return ResponseEntity.ok(response);
    }

    /**
     * Publishes a compiler stage event to all participants in a room.
     * Enriches the provided data map with the event name and fileId, then sends
     * it to /topic/room/{fileId}/compiler. All subscribers (the triggering host
     * and any watching guests) receive the message and update their output panel.
     * @param fileId the room/file ID that identifies the target WebSocket topic
     * @param event  the event name (e.g. "compilation_started", "execution_success")
     * @param data   stage-specific payload fields to include in the broadcast
     */
    private void broadcastToRoom(Long fileId, String event, Map<String, Object> data) {
        // Copy data into a mutable map so the event metadata fields can be added
        // without mutating the caller's original map
        Map<String, Object> message = new HashMap<>(data);
        message.put("event", event);
        message.put("fileId", fileId);

        messagingTemplate.convertAndSend("/topic/room/" + fileId + "/compiler", message);

        log.debug("📡 Broadcast to room {}: {}", fileId, event);
    }
}