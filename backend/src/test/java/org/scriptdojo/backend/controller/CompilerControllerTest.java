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

/**
 * Integration tests for {@link org.scriptdojo.backend.controller.CompilerController},
 * covering the POST /api/compiler/run endpoint.
 * Test structure:
 * - Authentication — unauthenticated requests are redirected to /login
 * - Valid code — successful compile and execute pipeline returns correct response shape
 * - Invalid code — syntax errors short-circuit at compilation and return error details
 * - Runtime errors — code that compiles but throws at runtime returns execution failure
 * - Response structure — compilation time, execution time, and class name are always present
 * - Multiline output — all printed lines are captured in the execution result
 * @WithMockUser is used for authenticated tests as no database user is required —
 * the compiler endpoint only checks that a session exists, not the user's identity.
 * fileId "1" is used as a placeholder across tests; no actual file record is needed
 * because the endpoint reads code from the request body rather than the database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CompilerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ─ Shared test fixtures

    /**
     * Well-formed Java code used by all happy-path tests.
     * Prints a known string so output assertions can match it exactly.
     */
    private static final String VALID_CODE = """
            public class HelloWorld {
                public static void main(String[] args) {
                    System.out.println("Hello from ScriptDojo!");
                }
            }
            """;

    /**
     * Java code with a missing semicolon — fails at the compilation stage.
     * Used to verify that compilation errors are correctly detected and returned.
     */
    private static final String INVALID_CODE = """
            public class BrokenCode {
                public static void main(String[] args) {
                    System.out.println("Missing semicolon")
                }
            }
            """;

    /**
     * Java code that compiles successfully but throws at runtime.
     * Used to verify that the pipeline correctly distinguishes compilation
     * success from execution failure.
     */
    private static final String RUNTIME_ERROR_CODE = """
            public class RuntimeError {
                public static void main(String[] args) {
                    throw new RuntimeException("Intentional error");
                }
            }
            """;

    /**
     * Builds a JSON request body containing the given code and fileId.
     * fileId is a placeholder — the endpoint does not look up a file record.
     */
    private String buildRequest(String code, String fileId) throws Exception {
        return objectMapper.writeValueAsString(Map.of("code", code, "fileId", fileId));
    }

    // ─ Authentication

    @Test
    @DisplayName("POST /api/compiler/run - redirects to login when not authenticated")
    void compileAndRun_unauthenticated_returns401() throws Exception {
        // No session present — Spring Security should redirect to /login rather
        // than allowing the request to reach the controller
        mockMvc.perform(post("/api/compiler/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(VALID_CODE, "1")))
                .andExpect(status().isFound())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    // ─ Valid code — happy path

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

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /api/compiler/run - valid code returns correct output")
    void compileAndRun_validCode_returnsCorrectOutput() throws Exception {
        // Verifies that the program's stdout is captured and returned in the response
        mockMvc.perform(post("/api/compiler/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(VALID_CODE, "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionResult.output").value(containsString("Hello from ScriptDojo!")));
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /api/compiler/run - valid code returns stage=execution")
    void compileAndRun_validCode_returnsStagedExecution() throws Exception {
        // stage="execution" confirms both stages ran — compilation succeeded and
        // execution was attempted
        mockMvc.perform(post("/api/compiler/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(VALID_CODE, "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("execution"));
    }

    // ─ Invalid code — compilation failure

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /api/compiler/run - invalid code returns success=false")
    void compileAndRun_invalidCode_returnsFailure() throws Exception {
        // Missing semicolon causes compilation to fail before execution is attempted
        mockMvc.perform(post("/api/compiler/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(INVALID_CODE, "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /api/compiler/run - invalid code returns stage=compilation")
    void compileAndRun_invalidCode_returnsStageCompilation() throws Exception {
        // stage="compilation" confirms the pipeline short-circuited before execution
        mockMvc.perform(post("/api/compiler/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(INVALID_CODE, "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("compilation"));
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /api/compiler/run - invalid code returns compilation errors")
    void compileAndRun_invalidCode_returnsErrors() throws Exception {
        // Verifies that structured CompilationError entries are returned in the response
        // rather than a generic error message
        mockMvc.perform(post("/api/compiler/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(INVALID_CODE, "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compilationResult.errors").isArray())
                .andExpect(jsonPath("$.compilationResult.errors", hasSize(greaterThan(0))));
    }

    // ─ Runtime errors — execution failure

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /api/compiler/run - runtime error returns success=false")
    void compileAndRun_runtimeError_returnsFailure() throws Exception {
        // Code compiles cleanly but throws at runtime — success should still be false
        mockMvc.perform(post("/api/compiler/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(RUNTIME_ERROR_CODE, "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /api/compiler/run - runtime error returns stage=execution")
    void compileAndRun_runtimeError_returnsStageExecution() throws Exception {
        // stage="execution" confirms compilation succeeded and the failure occurred
        // during the subprocess run rather than at compile time
        mockMvc.perform(post("/api/compiler/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(RUNTIME_ERROR_CODE, "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("execution"));
    }

    // ─ Response structure

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /api/compiler/run - response always includes compilationResult")
    void compileAndRun_responseAlwaysIncludesCompilationResult() throws Exception {
        // compilationResult must be present regardless of whether compilation succeeded
        mockMvc.perform(post("/api/compiler/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(VALID_CODE, "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compilationResult").exists());
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /api/compiler/run - response includes compilation time")
    void compileAndRun_validCode_returnsCompilationTime() throws Exception {
        // compilationTimeMs must be a numeric value — verifies the timing field is populated
        mockMvc.perform(post("/api/compiler/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(VALID_CODE, "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compilationResult.compilationTimeMs").isNumber());
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /api/compiler/run - response includes execution time")
    void compileAndRun_validCode_returnsExecutionTime() throws Exception {
        // executionTimeMs must be a numeric value — verifies the timing field is populated
        mockMvc.perform(post("/api/compiler/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(VALID_CODE, "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionResult.executionTimeMs").isNumber());
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /api/compiler/run - class name is correctly detected")
    void compileAndRun_validCode_correctClassName() throws Exception {
        // Verifies that extractClassName correctly identified "HelloWorld" from
        // the source code and included it in the response body
        MvcResult result = mockMvc.perform(post("/api/compiler/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(VALID_CODE, "1")))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertTrue(body.contains("HelloWorld"),
                "Response should reference the detected class name HelloWorld");
    }

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /api/compiler/run - successful execution returns exit code 0")
    void compileAndRun_validCode_returnsExitCodeZero() throws Exception {
        // Exit code 0 confirms the JVM subprocess terminated cleanly
        mockMvc.perform(post("/api/compiler/run")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(buildRequest(VALID_CODE, "1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionResult.exitCode").value(0));
    }

    // ─ Multiline output

    @Test
    @WithMockUser(username = "testuser")
    @DisplayName("POST /api/compiler/run - multiline output is captured")
    void compileAndRun_multilineOutput_capturedCorrectly() throws Exception {
        // Verifies that all lines printed to stdout are captured in a single
        // output string rather than only the first or last line
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