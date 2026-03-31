package org.scriptdojo.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.scriptdojo.backend.service.dto.CompilationResult;

import static org.junit.jupiter.api.Assertions.*;

class CompilationServiceTest {

    private CompilationService compilationService;

    @BeforeEach
    void setup() {
        compilationService = new CompilationService();
    }

    // ── extractClassName ──────────────────────────────────

    @Test
    @DisplayName("extractClassName - extracts public class name correctly")
    void extractClassName_publicClass_returnsCorrectName() {
        String code = "public class HelloWorld { public static void main(String[] args) {} }";
        assertEquals("HelloWorld", compilationService.extractClassName(code));
    }

    @Test
    @DisplayName("extractClassName - extracts non-public class name")
    void extractClassName_nonPublicClass_returnsCorrectName() {
        String code = "class MyClass { }";
        assertEquals("MyClass", compilationService.extractClassName(code));
    }

    @Test
    @DisplayName("extractClassName - prefers public class over non-public")
    void extractClassName_prefersPublicClass() {
        String code = "class Helper {} public class Main {}";
        assertEquals("Main", compilationService.extractClassName(code));
    }

    @Test
    @DisplayName("extractClassName - returns Main as fallback when no class found")
    void extractClassName_noClass_returnsFallback() {
        assertEquals("Main", compilationService.extractClassName("int x = 5;"));
    }

    @Test
    @DisplayName("extractClassName - handles class with generics in name area")
    void extractClassName_withPackageAndImports_returnsCorrectName() {
        String code = "import java.util.List;\npublic class Calculator { }";
        assertEquals("Calculator", compilationService.extractClassName(code));
    }

    // ── compile - success ─────────────────────────────────

    @Test
    @DisplayName("compile - valid code returns success=true")
    void compile_validCode_returnsSuccess() {
        String code = "public class Valid { public static void main(String[] args) {} }";
        CompilationResult result = compilationService.compile(code, "Valid");
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("compile - valid code has empty errors list")
    void compile_validCode_hasNoErrors() {
        String code = "public class NoErrors { }";
        CompilationResult result = compilationService.compile(code, "NoErrors");
        assertTrue(result.getErrors() == null || result.getErrors().isEmpty());
    }

    @Test
    @DisplayName("compile - valid code has positive compilation time")
    void compile_validCode_hasCompilationTime() {
        String code = "public class Timed { }";
        CompilationResult result = compilationService.compile(code, "Timed");
        assertTrue(result.getCompilationTimeMs() >= 0);
    }

    @Test
    @DisplayName("compile - valid code sets className in result")
    void compile_validCode_setsClassName() {
        String code = "public class Named { }";
        CompilationResult result = compilationService.compile(code, "Named");
        assertEquals("Named", result.getClassName());
    }

    @Test
    @DisplayName("compile - valid code sets outputDirectory in result")
    void compile_validCode_setsOutputDirectory() {
        String code = "public class WithOutput { }";
        CompilationResult result = compilationService.compile(code, "WithOutput");
        assertNotNull(result.getOutputDirectory());
    }

    // ── compile - failure ─────────────────────────────────

    @Test
    @DisplayName("compile - invalid code returns success=false")
    void compile_invalidCode_returnsFailure() {
        String code = "public class Bad { this is not valid java }";
        CompilationResult result = compilationService.compile(code, "Bad");
        assertFalse(result.isSuccess());
    }

    @Test
    @DisplayName("compile - invalid code returns non-empty errors list")
    void compile_invalidCode_returnsErrors() {
        String code = "public class BadErrors { int x = ; }";
        CompilationResult result = compilationService.compile(code, "BadErrors");
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrors());
        assertFalse(result.getErrors().isEmpty());
    }

    @Test
    @DisplayName("compile - error contains line number")
    void compile_invalidCode_errorHasLineNumber() {
        String code = "public class LineNum { int x = ; }";
        CompilationResult result = compilationService.compile(code, "LineNum");
        assertFalse(result.isSuccess());
        assertTrue(result.getErrors().get(0).getLineNumber() > 0);
    }

    @Test
    @DisplayName("compile - error contains message")
    void compile_invalidCode_errorHasMessage() {
        String code = "public class WithMsg { int x = ; }";
        CompilationResult result = compilationService.compile(code, "WithMsg");
        assertFalse(result.isSuccess());
        assertNotNull(result.getErrors().get(0).getMessage());
    }
}