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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String TEST_USERNAME = "filetest_user";

    // ── Setup ─────────────────────────────────────────────

    // Creates the test user in H2 once before any tests run
    // so @WithUserDetails can load a real CustomUserDetails with a valid ID
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

    // ── Helper: create a file and return its ID ───────────
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

    // ── GET /api/files ────────────────────────────────────

    // Tests that an authenticated user can retrieve their file list
    // by calling GET /api/files and expecting an array response
    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("GET /api/files - returns list of files for authenticated user")
    void getUserFiles_authenticated_returnsFileList() throws Exception {
        mockMvc.perform(get("/api/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    // Tests that an unauthenticated request to GET /api/files is rejected
    // by calling the endpoint without a session and expecting a redirect to login
    @Test
    @DisplayName("GET /api/files - redirects to login when not authenticated")
    void getUserFiles_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get("/api/files"))
                .andExpect(status().isFound());
    }

    // Tests that a newly created file appears in the user's file list
    // by creating a file and then checking it appears in GET /api/files
    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("GET /api/files - newly created file appears in list")
    void getUserFiles_afterCreation_containsNewFile() throws Exception {
        String uniqueName = "ListTest" + System.currentTimeMillis() + ".java";
        createTestFile(uniqueName);

        mockMvc.perform(get("/api/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem(uniqueName)));
    }

    // ── GET /api/files/{id} ───────────────────────────────

    // Tests that a specific file can be retrieved by its ID
    // by creating a file then fetching it by ID and checking the name matches
    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("GET /api/files/{id} - returns correct file by ID")
    void getFile_validId_returnsFile() throws Exception {
        String name = "GetTest" + System.currentTimeMillis() + ".java";
        Long id = createTestFile(name);

        mockMvc.perform(get("/api/files/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value(name));
    }

    // Tests that the file response includes all expected fields
    // by creating a file and checking id, name, content, language are all present
    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("GET /api/files/{id} - response includes all DTO fields")
    void getFile_validId_responseHasAllFields() throws Exception {
        Long id = createTestFile("FieldTest" + System.currentTimeMillis() + ".java");

        mockMvc.perform(get("/api/files/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.content").exists())
                .andExpect(jsonPath("$.language").exists())
                .andExpect(jsonPath("$.ownerId").exists());
    }

    // Tests that requesting a non-existent file ID returns a 500
    // by fetching an ID that does not exist in the database
    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("GET /api/files/{id} - returns 500 for non-existent file")
    void getFile_invalidId_returns500() {
        assertThrows(Exception.class, () ->
                mockMvc.perform(get("/api/files/999999")));
    }

    // ── POST /api/files ───────────────────────────────────

    // Tests that a new file is created successfully with valid data
    // by posting a file DTO and expecting a 200 response with the created file
    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("POST /api/files - creates file successfully with valid data")
    void createFile_validData_returns200() throws Exception {
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

    // Tests that the created file response contains the correct content
    // by checking the content field in the response matches what was sent
    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("POST /api/files - response contains correct content")
    void createFile_validData_responseHasCorrectContent() throws Exception {
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

    // Tests that the ownerId in the created file matches the authenticated user
    // by creating a file and checking the ownerId field is populated
    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("POST /api/files - response includes correct ownerId")
    void createFile_validData_responseHasOwnerId() throws Exception {
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

    // ── PUT /api/files/{id} ───────────────────────────────

    // Tests that a file's metadata can be updated with a PUT request
    // by creating a file and then updating its name via PUT /api/files/{id}
    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("PUT /api/files/{id} - updates file metadata successfully")
    void updateFile_validData_returns200() throws Exception {
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

    // ── PUT /api/files/{id}/content ───────────────────────

    // Tests that file content can be updated independently of metadata
    // by creating a file then calling PUT /api/files/{id}/content with new content
    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("PUT /api/files/{id}/content - updates file content successfully")
    void updateContent_validData_returns200() throws Exception {
        Long id = createTestFile("ContentUpdate" + System.currentTimeMillis() + ".java");
        String newContent = "public class Updated { // new content }";

        mockMvc.perform(put("/api/files/" + id + "/content")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(newContent))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(newContent));
    }

    // Tests that content update persists correctly by fetching the file after update
    // by updating content and then re-fetching the file to verify the change was saved
    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("PUT /api/files/{id}/content - content change persists on re-fetch")
    void updateContent_persistsAfterFetch() throws Exception {
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

    // ── PUT /api/files/{id}/rename ────────────────────────

    // Tests that a file can be renamed via the rename endpoint
    // by creating a file and then calling PUT /api/files/{id}/rename with a new name
    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("PUT /api/files/{id}/rename - renames file successfully")
    void renameFile_validName_returns200() throws Exception {
        Long id = createTestFile("OldName" + System.currentTimeMillis() + ".java");
        String newName = "NewName" + System.currentTimeMillis() + ".java";

        mockMvc.perform(put("/api/files/" + id + "/rename")
                        .param("newName", newName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(newName));
    }

    // Tests that the renamed file retains its original content after renaming
    // by checking the content field is unchanged after a rename operation
    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("PUT /api/files/{id}/rename - content is preserved after rename")
    void renameFile_contentPreservedAfterRename() throws Exception {
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

    // ── DELETE /api/files/{id} ────────────────────────────

    // Tests that a file can be deleted and returns 204 No Content
    // by creating a file and then deleting it with DELETE /api/files/{id}
    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("DELETE /api/files/{id} - deletes file and returns 204")
    void deleteFile_validId_returns204() throws Exception {
        Long id = createTestFile("DeleteMe" + System.currentTimeMillis() + ".java");

        mockMvc.perform(delete("/api/files/" + id))
                .andExpect(status().isNoContent());
    }

    // Tests that a deleted file can no longer be retrieved
    // by deleting a file then verifying GET /api/files/{id} returns 500
    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("DELETE /api/files/{id} - deleted file cannot be fetched")
    void deleteFile_deletedFileNotFound() throws Exception {
        Long id = createTestFile("GoneFile" + System.currentTimeMillis() + ".java");

        mockMvc.perform(delete("/api/files/" + id))
                .andExpect(status().isNoContent());

        assertThrows(Exception.class, () ->
                mockMvc.perform(get("/api/files/" + id)));
    }

    // Tests that deleting a file removes it from the user's file list
    // by creating a file, deleting it, and checking it no longer appears in GET /api/files
    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("DELETE /api/files/{id} - file no longer appears in file list")
    void deleteFile_removedFromFileList() throws Exception {
        String name = "RemoveFromList" + System.currentTimeMillis() + ".java";
        Long id = createTestFile(name);

        mockMvc.perform(get("/api/files"))
                .andExpect(jsonPath("$[*].name", hasItem(name)));

        mockMvc.perform(delete("/api/files/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/files"))
                .andExpect(jsonPath("$[*].name", not(hasItem(name))));
    }

    // Tests that attempting to delete a non-existent file returns 500
    // by calling DELETE with an ID that does not exist in the database
    @Test
    @WithUserDetails(TEST_USERNAME)
    @DisplayName("DELETE /api/files/{id} - returns 500 for non-existent file")
    void deleteFile_invalidId_returns500() {
        assertThrows(Exception.class, () ->
                mockMvc.perform(delete("/api/files/999999")));
    }
}