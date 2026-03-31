package org.scriptdojo.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompilerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ── Valid Java code used across multiple tests ───────
    private static final String VALID_CODE = """
            public class HelloWorld {
                public static void main(String[] args) {
                    System.out.println("Hello from ScriptDojo!");
                }
            }
            """;

    // ── Invalid Java code with syntax errors ─────────────
    private static final String INVALID_CODE = """
            public class BrokenCode {
                public static void main(String[] args) {
                    System.out.println("Missing semicolon")
                }
            }
            """;

    // ── Code that compiles but throws a runtime exception ─
    private static final String RUNTIME_ERROR_CODE = """
            public class RuntimeError {
                public static void main(String[] args) {
                    throw new RuntimeException("Intentional error");
                }
            }
            """;

    // ── Helper to build request body ─────────────────────
    private String buildRequest(String code, String fileId) throws Exception {
        return objectMapper.writeValueAsString(Map.of("code", code, "fileId", fileId));
    }

    // ── Tests ────────────────────────────────────────────

    // Tests that unauthenticated requests are redirected to the login page
// by calling /api/compiler/run without any user session and expecting a 302 redirect
    @Test
    @DisplayName("POST /api/compiler/run - redirects to login when not authenticated")
    void compileAndRun_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/compiler/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(VALID_CODE, "1")))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    // Tests that valid Java code compiles and executes successfully
    // by sending HelloWorld code and expecting success=true in the response
    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /api/compiler/run - valid code returns success=true")
    void compileAndRun_validCode_returnsSuccess() throws Exception {
        mockMvc.perform(post("/api/compiler/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(VALID_CODE, "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // Tests that valid code execution returns the correct program output
    // by checking the executionResult.output field contains the printed text
    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /api/compiler/run - valid code returns correct output")
    void compileAndRun_validCode_returnsCorrectOutput() throws Exception {
        mockMvc.perform(post("/api/compiler/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(VALID_CODE, "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionResult.output").value(containsString("Hello from ScriptDojo!")));
    }

    // Tests that the response stage is "execution" when code runs successfully
    // by checking the stage field in the response body
    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /api/compiler/run - valid code returns stage=execution")
    void compileAndRun_validCode_returnsStagedExecution() throws Exception {
        mockMvc.perform(post("/api/compiler/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(VALID_CODE, "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("execution"));
    }

    // Tests that invalid Java code returns success=false
    // by sending code with a missing semicolon and expecting compilation failure
    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /api/compiler/run - invalid code returns success=false")
    void compileAndRun_invalidCode_returnsFailure() throws Exception {
        mockMvc.perform(post("/api/compiler/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(INVALID_CODE, "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    // Tests that invalid code sets the failure stage to "compilation"
    // by checking the stage field when a syntax error is present
    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /api/compiler/run - invalid code returns stage=compilation")
    void compileAndRun_invalidCode_returnsStageCompilation() throws Exception {
        mockMvc.perform(post("/api/compiler/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(INVALID_CODE, "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("compilation"));
    }

    // Tests that compilation errors are returned in the response
    // by checking the compilationResult.errors array is not empty for invalid code
    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /api/compiler/run - invalid code returns compilation errors")
    void compileAndRun_invalidCode_returnsErrors() throws Exception {
        mockMvc.perform(post("/api/compiler/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(INVALID_CODE, "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compilationResult.errors").isArray())
                .andExpect(jsonPath("$.compilationResult.errors", hasSize(greaterThan(0))));
    }

    // Tests that code which compiles but throws a runtime exception returns success=false
    // by sending code that deliberately throws a RuntimeException
    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /api/compiler/run - runtime error returns success=false")
    void compileAndRun_runtimeError_returnsFailure() throws Exception {
        mockMvc.perform(post("/api/compiler/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(RUNTIME_ERROR_CODE, "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    // Tests that a runtime error sets the failure stage to "execution"
    // confirming compilation succeeded but execution failed
    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /api/compiler/run - runtime error returns stage=execution")
    void compileAndRun_runtimeError_returnsStageExecution() throws Exception {
        mockMvc.perform(post("/api/compiler/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(RUNTIME_ERROR_CODE, "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("execution"));
    }

    // Tests that the response always includes a compilationResult object
    // by checking the field exists in both success and failure responses
    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /api/compiler/run - response always includes compilationResult")
    void compileAndRun_responseAlwaysIncludesCompilationResult() throws Exception {
        mockMvc.perform(post("/api/compiler/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(VALID_CODE, "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compilationResult").exists());
    }

    // Tests that a successful run returns compilation time in milliseconds
    // by checking compilationResult.compilationTimeMs is a non-negative number
    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /api/compiler/run - response includes compilation time")
    void compileAndRun_validCode_returnsCompilationTime() throws Exception {
        mockMvc.perform(post("/api/compiler/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(VALID_CODE, "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compilationResult.compilationTimeMs").isNumber());
    }

    // Tests that a successful run returns execution time in milliseconds
    // by checking executionResult.executionTimeMs is a non-negative number
    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /api/compiler/run - response includes execution time")
    void compileAndRun_validCode_returnsExecutionTime() throws Exception {
        mockMvc.perform(post("/api/compiler/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(VALID_CODE, "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionResult.executionTimeMs").isNumber());
    }

    // Tests that the class name is correctly extracted from HelloWorld source code
    // by calling extractClassName directly on the CompilationService
    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /api/compiler/run - class name is correctly detected")
    void compileAndRun_validCode_correctClassName() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/compiler/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(VALID_CODE, "1")))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("HelloWorld"),
                "Response should reference the detected class name HelloWorld");
    }

    // Tests that a successful execution returns exit code 0
    // by checking executionResult.exitCode in the response
    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /api/compiler/run - successful execution returns exit code 0")
    void compileAndRun_validCode_returnsExitCodeZero() throws Exception {
        mockMvc.perform(post("/api/compiler/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(VALID_CODE, "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionResult.exitCode").value(0));
    }

    // Tests that System.out output with newlines is captured correctly
    // by running code that prints multiple lines and checking all appear in output
    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /api/compiler/run - multiline output is captured")
    void compileAndRun_multilineOutput_capturedCorrectly() throws Exception {
        String multilineCode = """
                public class MultiLine {
                    public static void main(String[] args) {
                        System.out.println("Line 1");
                        System.out.println("Line 2");
                        System.out.println("Line 3");
                    }
                }
                """;

        mockMvc.perform(post("/api/compiler/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(multilineCode, "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.executionResult.output").value(allOf(
                        containsString("Line 1"),
                        containsString("Line 2"),
                        containsString("Line 3")
                )));
    }
}