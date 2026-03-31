package org.scriptdojo.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.scriptdojo.backend.service.dto.CompilationResult;
import org.scriptdojo.backend.service.dto.ExecutionResult;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ExecutionServiceTest {

    private ExecutionService executionService;
    private CompilationService compilationService;

    @BeforeEach
    void setup() {
        executionService = new ExecutionService();
        compilationService = new CompilationService();
    }

    // ── Helper ────────────────────────────────────────────

    private Path compileAndGetPath(String code, String className) {
        CompilationResult result = compilationService.compile(code, className);
        assertTrue(result.isSuccess(), "Compilation failed — test setup error");
        return Path.of(result.getOutputDirectory());
    }

    // ── execute - success ─────────────────────────────────

    @Test
    @DisplayName("execute - valid program returns success=true")
    void execute_validProgram_returnsSuccess() {
        String code = "public class ExecSuccess { public static void main(String[] args) { System.out.println(\"ok\"); } }";
        Path path = compileAndGetPath(code, "ExecSuccess");
        ExecutionResult result = executionService.execute("ExecSuccess", path);
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("execute - program output is captured correctly")
    void execute_validProgram_capturesOutput() {
        String code = "public class ExecOutput { public static void main(String[] args) { System.out.println(\"hello world\"); } }";
        Path path = compileAndGetPath(code, "ExecOutput");
        ExecutionResult result = executionService.execute("ExecOutput", path);
        assertTrue(result.getOutput().contains("hello world"));
    }

    @Test
    @DisplayName("execute - exit code is 0 for successful program")
    void execute_validProgram_exitCodeIsZero() {
        String code = "public class ExecExit { public static void main(String[] args) {} }";
        Path path = compileAndGetPath(code, "ExecExit");
        ExecutionResult result = executionService.execute("ExecExit", path);
        assertEquals(0, result.getExitCode());
    }

    @Test
    @DisplayName("execute - execution time is recorded")
    void execute_validProgram_executionTimeRecorded() {
        String code = "public class ExecTime { public static void main(String[] args) {} }";
        Path path = compileAndGetPath(code, "ExecTime");
        ExecutionResult result = executionService.execute("ExecTime", path);
        assertTrue(result.getExecutionTimeMs() >= 0);
    }

    @Test
    @DisplayName("execute - multiline output is captured correctly")
    void execute_multilineOutput_capturedCorrectly() {
        String code = "public class ExecMulti { public static void main(String[] args) { " +
                "System.out.println(\"line1\"); System.out.println(\"line2\"); System.out.println(\"line3\"); } }";
        Path path = compileAndGetPath(code, "ExecMulti");
        ExecutionResult result = executionService.execute("ExecMulti", path);
        assertTrue(result.getOutput().contains("line1"));
        assertTrue(result.getOutput().contains("line2"));
        assertTrue(result.getOutput().contains("line3"));
    }

    // ── execute - runtime errors ──────────────────────────

    @Test
    @DisplayName("execute - program that throws exception returns success=false")
    void execute_programThrowsException_returnsFailure() {
        String code = "public class ExecThrows { public static void main(String[] args) { throw new RuntimeException(\"boom\"); } }";
        Path path = compileAndGetPath(code, "ExecThrows");
        ExecutionResult result = executionService.execute("ExecThrows", path);
        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("execute - program that throws exception has non-zero exit code")
    void execute_programThrowsException_nonZeroExitCode() {
        String code = "public class ExecExitCode { public static void main(String[] args) { throw new RuntimeException(\"boom\"); } }";
        Path path = compileAndGetPath(code, "ExecExitCode");
        ExecutionResult result = executionService.execute("ExecExitCode", path);
        assertNotEquals(0, result.getExitCode());
    }

    @Test
    @DisplayName("execute - runtime error output is captured")
    void execute_programThrowsException_errorCaptured() {
        String code = "public class ExecError { public static void main(String[] args) { throw new RuntimeException(\"test error\"); } }";
        Path path = compileAndGetPath(code, "ExecError");
        ExecutionResult result = executionService.execute("ExecError", path);
        assertNotNull(result.getError());
        assertFalse(result.getError().isEmpty());
    }

    @Test
    @DisplayName("execute - System.exit with non-zero code returns success=false")
    void execute_systemExitNonZero_returnsFailure() {
        String code = "public class ExecSysExit { public static void main(String[] args) { System.exit(1); } }";
        Path path = compileAndGetPath(code, "ExecSysExit");
        ExecutionResult result = executionService.execute("ExecSysExit", path);
        assertFalse(result.isSuccess());
    }
}