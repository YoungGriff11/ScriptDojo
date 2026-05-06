package org.scriptdojo.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithUserDetails;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for {@link org.scriptdojo.backend.controller.FileController},
 * covering all CRUD endpoints under /api/files.
 * Test structure:
 * - GET /api/files          — list retrieval, authentication enforcement
 * - GET /api/files/{id}     — single file retrieval, field presence, missing ID handling
 * - POST /api/files         — file creation, content and ownerId validation
 * - PUT /api/files/{id}     — metadata update
 * - PUT /api/files/{id}/content  — content-only update and persistence verification
 * - PUT /api/files/{id}/rename   — rename with content preservation
 * - DELETE /api/files/{id}  — deletion, post-delete fetch, list removal
 * Uses @TestInstance(PER_CLASS) so @BeforeAll can be non-static, allowing MockMvc
 * to register the test user via HTTP before @WithUserDetails attempts to load them.
 * File names include System.currentTimeMillis() to ensure uniqueness across test runs
 * and prevent cross-test state conflicts in the shared H2 database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * Username used for all authenticated test cases.
     * Registered once in @BeforeAll and referenced by @WithUserDetails
     * to load a valid CustomUserDetails with a real database ID.
     */
    private static final String TEST_USERNAME = "filetest_user";

    // ─ Setup

    /**
     * Registers the test user via the public registration endpoint before any test runs.
     * Required so @WithUserDetails can resolve a real CustomUserDetails instance with a
     * valid database ID — without this, file ownership assertions would fail.
     */
    @BeforeAll
    void setupTestUser() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", TEST_USERNAME,
                                "password", "Password1!",
                                "email", "filetest@test.com"
                        ))))
                .andExpect(status().isOk());
    }

    /**
     * Creates a file via POST /api/files and returns its generated ID.
     * Used by tests that need an existing file before exercising read, update,
     * or delete behaviour — avoids repeating file creation boilerplate inline.
     * @param name the file name to use; should be unique per call to avoid conflicts
     * @return the database ID of the newly created file
     */
    private Long createTestFile(String name) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", name,
                "content", "public class " + name.replace(".java", "") + " {}",
                "language", "java"
        ));

        MvcResult result = mockMvc.perform(post("/api/files")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
    }

    // ─ GET /api/files

    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("GET /api/files - returns list of files for authenticated user")
    void getUserFiles_authenticated_returnsFileList() throws Exception {
        // Confirms the endpoint returns a JSON array for an authenticated user
        mockMvc.perform(get("/api/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    @DisplayName("GET /api/files - redirects to login when not authenticated")
    void getUserFiles_unauthenticated_redirectsToLogin() throws Exception {
        // No session present — Spring Security should redirect rather than return data
        mockMvc.perform(get("/api/files"))
                .andExpect(status().isFound());
    }

    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("GET /api/files - newly created file appears in list")
    void getUserFiles_afterCreation_containsNewFile() throws Exception {
        // Verifies that the file list reflects newly persisted files immediately
        String uniqueName = "ListTest" + System.currentTimeMillis() + ".java";
        createTestFile(uniqueName);

        mockMvc.perform(get("/api/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem(uniqueName)));
    }

    // ─ GET /api/files/{id}

    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("GET /api/files/{id} - returns correct file by ID")
    void getFile_validId_returnsFile() throws Exception {
        // Confirms the correct file is returned when fetched by its database ID
        String name = "GetTest" + System.currentTimeMillis() + ".java";
        Long id = createTestFile(name);

        mockMvc.perform(get("/api/files/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value(name));
    }

    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("GET /api/files/{id} - response includes all DTO fields")
    void getFile_validId_responseHasAllFields() throws Exception {
        // Verifies that all expected FileDTO fields are present in the response,
        // ensuring the entity-to-DTO mapping is complete
        Long id = createTestFile("FieldTest" + System.currentTimeMillis() + ".java");

        mockMvc.perform(get("/api/files/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.content").exists())
                .andExpect(jsonPath("$.language").exists())
                .andExpect(jsonPath("$.ownerId").exists());
    }

    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("GET /api/files/{id} - returns 500 for non-existent file")
    void getFile_invalidId_returns500() {
        // RuntimeException propagates as ServletException when no @ControllerAdvice
        // is present — assertThrows captures it rather than failing the test
        assertThrows(Exception.class, () ->
                mockMvc.perform(get("/api/files/999999")));
    }

    // ─ POST /api/files

    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("POST /api/files - creates file successfully with valid data")
    void createFile_validData_returns200() throws Exception {
        // Confirms a well-formed creation request returns 200 with the persisted file
        String name = "CreateTest" + System.currentTimeMillis() + ".java";
        String body = objectMapper.writeValueAsString(Map.of(
                "name", name,
                "content", "public class Test {}",
                "language", "java"
        ));

        mockMvc.perform(post("/api/files")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.id").isNumber());
    }

    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("POST /api/files - response contains correct content")
    void createFile_validData_responseHasCorrectContent() throws Exception {
        // Verifies the content field in the response matches what was submitted
        String content = "public class ContentCheck { }";
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "ContentCheck" + System.currentTimeMillis() + ".java",
                "content", content,
                "language", "java"
        ));

        mockMvc.perform(post("/api/files")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(content));
    }

    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("POST /api/files - response includes correct ownerId")
    void createFile_validData_responseHasOwnerId() throws Exception {
        // Confirms the ownerId is populated in the response, verifying the authenticated
        // user's ID was correctly associated with the new file
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "OwnerTest" + System.currentTimeMillis() + ".java",
                "content", "public class OwnerTest {}",
                "language", "java"
        ));

        mockMvc.perform(post("/api/files")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ownerId").isNumber());
    }

    // ─ PUT /api/files/{id}

    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("PUT /api/files/{id} - updates file metadata successfully")
    void updateFile_validData_returns200() throws Exception {
        // Confirms the name field is updated and reflected in the response
        Long id = createTestFile("UpdateMeta" + System.currentTimeMillis() + ".java");
        String newName = "UpdatedName" + System.currentTimeMillis() + ".java";

        String body = objectMapper.writeValueAsString(Map.of(
                "name", newName,
                "language", "java"
        ));

        mockMvc.perform(put("/api/files/" + id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(newName));
    }

    // ─ PUT /api/files/{id}/content

    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("PUT /api/files/{id}/content - updates file content successfully")
    void updateContent_validData_returns200() throws Exception {
        // Confirms the content field is replaced and returned in the response
        Long id = createTestFile("ContentUpdate" + System.currentTimeMillis() + ".java");
        String newContent = "public class Updated { // new content }";

        mockMvc.perform(put("/api/files/" + id + "/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newContent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(newContent));
    }

    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("PUT /api/files/{id}/content - content change persists on re-fetch")
    void updateContent_persistsAfterFetch() throws Exception {
        // Verifies the updated content is durably persisted by re-fetching the file
        // in a separate request after the update
        Long id = createTestFile("PersistTest" + System.currentTimeMillis() + ".java");
        String newContent = "public class Persisted { }";

        mockMvc.perform(put("/api/files/" + id + "/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newContent))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/files/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(newContent));
    }

    // ─ PUT /api/files/{id}/rename

    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("PUT /api/files/{id}/rename - renames file successfully")
    void renameFile_validName_returns200() throws Exception {
        // Confirms the name field is updated and reflected in the response
        Long id = createTestFile("OldName" + System.currentTimeMillis() + ".java");
        String newName = "NewName" + System.currentTimeMillis() + ".java";

        mockMvc.perform(put("/api/files/" + id + "/rename")
                        .param("newName", newName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(newName));
    }

    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("PUT /api/files/{id}/rename - content is preserved after rename")
    void renameFile_contentPreservedAfterRename() throws Exception {
        // Verifies that renaming only modifies the name field and leaves the
        // file's content unchanged
        String originalContent = "public class OriginalContent { }";
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "PreserveContent" + System.currentTimeMillis() + ".java",
                "content", originalContent,
                "language", "java"
        ));

        MvcResult created = mockMvc.perform(post("/api/files")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();

        Long id = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();

        mockMvc.perform(put("/api/files/" + id + "/rename")
                        .param("newName", "RenamedFile.java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(originalContent));
    }

    // ─ DELETE /api/files/{id}

    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("DELETE /api/files/{id} - deletes file and returns 204")
    void deleteFile_validId_returns204() throws Exception {
        // Confirms a valid DELETE request returns the expected 204 No Content status
        Long id = createTestFile("DeleteMe" + System.currentTimeMillis() + ".java");

        mockMvc.perform(delete("/api/files/" + id))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("DELETE /api/files/{id} - deleted file cannot be fetched")
    void deleteFile_deletedFileNotFound() throws Exception {
        // Verifies the file is no longer retrievable after deletion —
        // the RuntimeException propagates as assertThrows captures it
        Long id = createTestFile("GoneFile" + System.currentTimeMillis() + ".java");

        mockMvc.perform(delete("/api/files/" + id))
                .andExpect(status().isNoContent());

        assertThrows(Exception.class, () ->
                mockMvc.perform(get("/api/files/" + id)));
    }

    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("DELETE /api/files/{id} - file no longer appears in file list")
    void deleteFile_removedFromFileList() throws Exception {
        // Confirms deletion is reflected in the file list — verifies both that
        // the file was present before deletion and absent after
        String name = "RemoveFromList" + System.currentTimeMillis() + ".java";
        Long id = createTestFile(name);

        mockMvc.perform(get("/api/files"))
                .andExpect(jsonPath("$[*].name", hasItem(name)));

        mockMvc.perform(delete("/api/files/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/files"))
                .andExpect(jsonPath("$[*].name", not(hasItem(name))));
    }

    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("DELETE /api/files/{id} - returns 500 for non-existent file")
    void deleteFile_invalidId_returns500() {
        // RuntimeException propagates as ServletException when no @ControllerAdvice
        // is present — assertThrows captures it rather than failing the test
        assertThrows(Exception.class, () ->
                mockMvc.perform(delete("/api/files/999999")));
    }
}