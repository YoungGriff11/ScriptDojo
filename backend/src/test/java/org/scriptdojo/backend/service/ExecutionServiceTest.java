package org.scriptdojo.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.scriptdojo.backend.service.dto.CompilationResult;
import org.scriptdojo.backend.service.dto.ExecutionResult;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link ExecutionService}, verifying subprocess execution behaviour
 * for compiled Java programs covering both successful runs and runtime failures.
 * Test structure:
 * - execute (success)        — success flag, stdout capture, exit code 0,
 *                              execution time recorded, multiline output captured
 * - execute (runtime errors) — success flag false, non-zero exit code, stderr captured,
 *                              System.exit with non-zero code treated as failure
 * Each test compiles a self-contained Java snippet using a real CompilationService
 * instance before invoking ExecutionService. The compileAndGetPath helper asserts
 * that compilation succeeded so any assertion failure clearly indicates an execution
 * problem rather than a setup problem.
 * No Spring context is loaded; both services are instantiated directly. A JDK must
 * be present on the test classpath and "java" must be resolvable on the system PATH
 * for the subprocess to launch correctly.
 */
class ExecutionServiceTest {

    // Fresh instances per test to ensure complete state isolation
    private ExecutionService executionService;
    private CompilationService compilationService;

    @BeforeEach
    void setup() {
        executionService = new ExecutionService();
        compilationService = new CompilationService();
    }

    // ─ Helper

    /**
     * Compiles the given source code and returns the output directory path.
     * Asserts that compilation succeeded so that a failure here is clearly
     * identified as a test setup problem rather than an execution failure.
     * @param code      the Java source code to compile
     * @param className the public class name in the source
     * @return the path to the directory containing the compiled .class file
     */
    private Path compileAndGetPath(String code, String className) {
        CompilationResult result = compilationService.compile(code, className);
        assertTrue(result.isSuccess(), "Compilation failed — test setup error");
        return Path.of(result.getOutputDirectory());
    }

    // ─ execute — success

    @Test
    @DisplayName("execute - valid program returns success=true")
    void execute_validProgram_returnsSuccess() {
        // Confirms the primary success flag is set when the program exits cleanly
        String code = "public class ExecSuccess { public static void main(String[] args) { System.out.println(\"ok\"); } }";
        Path path = compileAndGetPath(code, "ExecSuccess");
        ExecutionResult result = executionService.execute("ExecSuccess", path);
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("execute - program output is captured correctly")
    void execute_validProgram_capturesOutput() {
        // Confirms stdout from the subprocess is captured and returned in the result
        String code = "public class ExecOutput { public static void main(String[] args) { System.out.println(\"hello world\"); } }";
        Path path = compileAndGetPath(code, "ExecOutput");
        ExecutionResult result = executionService.execute("ExecOutput", path);
        assertTrue(result.getOutput().contains("hello world"));
    }

    @Test
    @DisplayName("execute - exit code is 0 for successful program")
    void execute_validProgram_exitCodeIsZero() {
        // Confirms exit code 0 is reported for a program that terminates normally
        String code = "public class ExecExit { public static void main(String[] args) {} }";
        Path path = compileAndGetPath(code, "ExecExit");
        ExecutionResult result = executionService.execute("ExecExit", path);
        assertEquals(0, result.getExitCode());
    }

    @Test
    @DisplayName("execute - execution time is recorded")
    void execute_validProgram_executionTimeRecorded() {
        // Confirms executionTimeMs is populated — 0 is acceptable for near-instant
        // programs, so >= 0 is used rather than > 0
        String code = "public class ExecTime { public static void main(String[] args) {} }";
        Path path = compileAndGetPath(code, "ExecTime");
        ExecutionResult result = executionService.execute("ExecTime", path);
        assertTrue(result.getExecutionTimeMs() >= 0);
    }

    @Test
    @DisplayName("execute - multiline output is captured correctly")
    void execute_multilineOutput_capturedCorrectly() {
        // Confirms all lines printed to stdout are captured in a single output string
        // rather than only the first or last line
        String code = "public class ExecMulti { public static void main(String[] args) { " +
                "System.out.println(\"line1\"); System.out.println(\"line2\"); System.out.println(\"line3\"); } }";
        Path path = compileAndGetPath(code, "ExecMulti");
        ExecutionResult result = executionService.execute("ExecMulti", path);
        assertTrue(result.getOutput().contains("line1"));
        assertTrue(result.getOutput().contains("line2"));
        assertTrue(result.getOutput().contains("line3"));
    }

    // ─ execute — runtime errors

    @Test
    @DisplayName("execute - program that throws exception returns success=false")
    void execute_programThrowsException_returnsFailure() {
        // Confirms an uncaught exception in the program results in a failure result
        String code = "public class ExecThrows { public static void main(String[] args) { throw new RuntimeException(\"boom\"); } }";
        Path path = compileAndGetPath(code, "ExecThrows");
        ExecutionResult result = executionService.execute("ExecThrows", path);
        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("execute - program that throws exception has non-zero exit code")
    void execute_programThrowsException_nonZeroExitCode() {
        // The JVM returns a non-zero exit code when a program terminates due to an
        // uncaught exception — confirms this is correctly reflected in the result
        String code = "public class ExecExitCode { public static void main(String[] args) { throw new RuntimeException(\"boom\"); } }";
        Path path = compileAndGetPath(code, "ExecExitCode");
        ExecutionResult result = executionService.execute("ExecExitCode", path);
        assertNotEquals(0, result.getExitCode());
    }

    @Test
    @DisplayName("execute - runtime error output is captured")
    void execute_programThrowsException_errorCaptured() {
        // Confirms the JVM's exception stack trace written to stderr is captured
        // in the error field so it can be displayed in the output panel
        String code = "public class ExecError { public static void main(String[] args) { throw new RuntimeException(\"test error\"); } }";
        Path path = compileAndGetPath(code, "ExecError");
        ExecutionResult result = executionService.execute("ExecError", path);
        assertNotNull(result.getError());
        assertFalse(result.getError().isEmpty());
    }

    @Test
    @DisplayName("execute - System.exit with non-zero code returns success=false")
    void execute_systemExitNonZero_returnsFailure() {
        // Confirms that an explicit System.exit(1) is treated as a failure —
        // success is determined solely by the exit code being 0
        String code = "public class ExecSysExit { public static void main(String[] args) { System.exit(1); } }";
        Path path = compileAndGetPath(code, "ExecSysExit");
        ExecutionResult result = executionService.execute("ExecSysExit", path);
        assertFalse(result.isSuccess());
    }
}