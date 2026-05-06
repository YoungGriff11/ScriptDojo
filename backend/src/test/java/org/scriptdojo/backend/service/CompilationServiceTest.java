package org.scriptdojo.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.scriptdojo.backend.service.dto.CompilationResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CompilationService}, verifying class name extraction
 * and the compile-time behaviour for both valid and invalid Java source code.
 * Test structure:
 * - extractClassName — public class, non-public class, public preferred over non-public,
 *                      fallback when no class found, class with imports
 * - compile (success) — success flag, empty errors, compilation time, className,
 *                       and outputDirectory fields populated correctly
 * - compile (failure) — success flag false, non-empty errors list, error line number
 *                       and message populated for invalid source
 * A fresh CompilationService instance is created before each test via @BeforeEach
 * to ensure complete state isolation. No Spring context is loaded; the service is
 * instantiated directly. These tests invoke the real javax.tools.JavaCompiler and
 * write to the system temp directory — a JDK must be present on the test classpath.
 */
class CompilationServiceTest {

    // Fresh instance per test — the service is stateless but instantiated cleanly
    // to mirror production behaviour and avoid any unforeseen shared state
    private CompilationService compilationService;

    @BeforeEach
    void setup() {
        compilationService = new CompilationService();
    }

    // ─ extractClassName

    @Test
    @DisplayName("extractClassName - extracts public class name correctly")
    void extractClassName_publicClass_returnsCorrectName() {
        // Standard case — public class declaration should be matched first
        String code = "public class HelloWorld { public static void main(String[] args) {} }";
        assertEquals("HelloWorld", compilationService.extractClassName(code));
    }

    @Test
    @DisplayName("extractClassName - extracts non-public class name")
    void extractClassName_nonPublicClass_returnsCorrectName() {
        // Falls back to the package-private class pattern when no public class is present
        String code = "class MyClass { }";
        assertEquals("MyClass", compilationService.extractClassName(code));
    }

    @Test
    @DisplayName("extractClassName - prefers public class over non-public")
    void extractClassName_prefersPublicClass() {
        // When both a public and a non-public class are declared, the public one
        // must be returned because the Java compiler requires the filename to match it
        String code = "class Helper {} public class Main {}";
        assertEquals("Main", compilationService.extractClassName(code));
    }

    @Test
    @DisplayName("extractClassName - returns Main as fallback when no class found")
    void extractClassName_noClass_returnsFallback() {
        // Neither pattern matches — the "Main" fallback allows the pipeline to continue
        // rather than failing before compilation is attempted
        assertEquals("Main", compilationService.extractClassName("int x = 5;"));
    }

    @Test
    @DisplayName("extractClassName - handles class with generics in name area")
    void extractClassName_withPackageAndImports_returnsCorrectName() {
        // Confirms the regex correctly skips import statements and isolates the class name
        String code = "import java.util.List;\npublic class Calculator { }";
        assertEquals("Calculator", compilationService.extractClassName(code));
    }

    // ─ compile — success

    @Test
    @DisplayName("compile - valid code returns success=true")
    void compile_validCode_returnsSuccess() {
        // Confirms the primary success flag is set when compilation produces no errors
        String code = "public class Valid { public static void main(String[] args) {} }";
        CompilationResult result = compilationService.compile(code, "Valid");
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("compile - valid code has empty errors list")
    void compile_validCode_hasNoErrors() {
        // Confirms the errors list is null or empty on a successful compilation —
        // callers must be able to safely iterate without null checks
        String code = "public class NoErrors { }";
        CompilationResult result = compilationService.compile(code, "NoErrors");
        assertTrue(result.getErrors() == null || result.getErrors().isEmpty());
    }

    @Test
    @DisplayName("compile - valid code has positive compilation time")
    void compile_validCode_hasCompilationTime() {
        // Confirms the compilationTimeMs field is populated — 0 is acceptable for
        // near-instant compilations, so >= 0 is used rather than > 0
        String code = "public class Timed { }";
        CompilationResult result = compilationService.compile(code, "Timed");
        assertTrue(result.getCompilationTimeMs() >= 0);
    }

    @Test
    @DisplayName("compile - valid code sets className in result")
    void compile_validCode_setsClassName() {
        // Confirms the className field is carried through to the result so
        // ExecutionService can locate the correct class to invoke
        String code = "public class Named { }";
        CompilationResult result = compilationService.compile(code, "Named");
        assertEquals("Named", result.getClassName());
    }

    @Test
    @DisplayName("compile - valid code sets outputDirectory in result")
    void compile_validCode_setsOutputDirectory() {
        // Confirms the outputDirectory field is populated so ExecutionService knows
        // where to find the compiled .class file
        String code = "public class WithOutput { }";
        CompilationResult result = compilationService.compile(code, "WithOutput");
        assertNotNull(result.getOutputDirectory());
    }

    // ─ compile — failure

    @Test
    @DisplayName("compile - invalid code returns success=false")
    void compile_invalidCode_returnsFailure() {
        // Confirms the primary failure flag is set when the source has syntax errors
        String code = "public class Bad { this is not valid java }";
        CompilationResult result = compilationService.compile(code, "Bad");
        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("compile - invalid code returns non-empty errors list")
    void compile_invalidCode_returnsErrors() {
        // Confirms at least one structured CompilationError is returned so the
        // frontend has specific error details to display in the output panel
        String code = "public class BadErrors { int x = ; }";
        CompilationResult result = compilationService.compile(code, "BadErrors");
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrors());
        assertFalse(result.getErrors().isEmpty());
    }

    @Test
    @DisplayName("compile - error contains line number")
    void compile_invalidCode_errorHasLineNumber() {
        // Confirms the line number field is populated so the frontend can highlight
        // the correct line in the Monaco Editor
        String code = "public class LineNum { int x = ; }";
        CompilationResult result = compilationService.compile(code, "LineNum");
        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().get(0).getLineNumber() > 0);
    }

    @Test
    @DisplayName("compile - error contains message")
    void compile_invalidCode_errorHasMessage() {
        // Confirms the human-readable error message is populated so it can be
        // displayed in the output panel alongside the line number
        String code = "public class WithMsg { int x = ; }";
        CompilationResult result = compilationService.compile(code, "WithMsg");
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrors().get(0).getMessage());
    }
}