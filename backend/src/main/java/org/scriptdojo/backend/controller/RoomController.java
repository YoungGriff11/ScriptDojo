package org.scriptdojo.backend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.scriptdojo.backend.entity.FileEntity;
import org.scriptdojo.backend.entity.RoomEntity;
import org.scriptdojo.backend.repository.RoomRepository;
import org.scriptdojo.backend.service.FileService;
import org.scriptdojo.backend.security.CustomUserDetails;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Random;

/**
 * Controller for creating and joining collaborative editing rooms in ScriptDojo.
 * A room is a lightweight join record that associates a shareable ID with a
 * file and its host. Guests navigate to the share URL, which the React frontend
 * resolves by calling the join endpoint to retrieve the file content and an
 * assigned guest name — no account or login is required.
 * Room lifecycle:
 *   1. Host calls POST /api/room/create → a RoomEntity is persisted and a
 *      shareable URL is returned containing the generated room ID.
 *   2. Host shares the URL with guests (e.g. via chat or email).
 *   3. Guest navigates to /room/{roomId} in their browser → the React frontend
 *      calls GET /api/room/join/{roomId} to fetch the file content and guest name,
 *      then connects to the WebSocket to begin the collaborative session.
 * The share URL base is configurable via the {@code app.base-url} property so
 * that the same codebase works correctly in local development, Docker, and
 * production environments without code changes.
 * Note: Uses @Controller rather than @RestController because SpaController
 * (which serves index.html for React routes) is also registered in this package.
 * Each endpoint that returns a response body is annotated with @ResponseBody explicitly.
 */
@Controller
@RequiredArgsConstructor // Lombok: generates constructor injecting roomRepository and fileService
@Slf4j                   // Lombok: injects a static SLF4J logger
public class RoomController {

    // Direct repository access used here because room creation is a simple
    // persist-and-return operation that does not warrant a dedicated service layer
    private final RoomRepository roomRepository;

    // Used to load the file associated with a room when a guest joins
    private final FileService fileService;

    /**
     * The base URL prepended to room IDs when generating shareable links.
     * Resolved from the {@code app.base-url} application property, defaulting
     * to http://localhost:8080 if the property is not set. Should be set to
     * the publicly accessible host in Docker and production deployments.
     */
    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

    /**
     * Creates a new collaboration room for a given file and returns a shareable URL.
     * Generates a unique 11-character alphanumeric room ID, persists a RoomEntity
     * linking it to the file and the authenticated host, then constructs and returns
     * the URL that guests can use to join the session.
     * POST /api/room/create?fileId={fileId}
     * @param fileId the ID of the file to share; the room is associated with this file
     * @param auth   the authentication of the host creating the room; used to record
     *               the host ID on the room entity
     * @return 200 OK with a JSON object containing:
     *         - "roomId": the generated room identifier
     *         - "url":    the full shareable URL guests should navigate to
     */
    @PostMapping("/api/room/create")
    @ResponseBody
    public ResponseEntity<Map<String, String>> createRoom(
            @RequestParam Long fileId,
            Authentication auth
    ) {
        // Cast to CustomUserDetails to access the database user ID.
        // Safe because CustomUserDetailsService always produces CustomUserDetails
        // instances for authenticated sessions.
        CustomUserDetails user = (CustomUserDetails) auth.getPrincipal();
        Long hostId = user.getId();

        String roomId = generateRoomId();

        // Build and persist the room record — links the generated ID to the file and host
        RoomEntity room = new RoomEntity();
        room.setId(roomId);
        room.setFileId(fileId);
        room.setHostId(hostId);
        room.setCreatedAt(LocalDateTime.now());

        roomRepository.save(room);

        // Construct the shareable URL using the configurable base URL so the link
        // works correctly across local, Docker, and production environments
        String shareUrl = baseUrl + "/room/" + roomId;

        log.info("════════════════════════════════════════════════════");
        log.info("🔗 ROOM CREATED");
        log.info("   Room ID: {}", roomId);
        log.info("   File ID: {}", fileId);
        log.info("   Host ID: {}", hostId);
        log.info("   Share URL: {}", shareUrl);
        log.info("════════════════════════════════════════════════════");

        return ResponseEntity.ok(Map.of(
                "roomId", roomId,
                "url", shareUrl
        ));
    }

    /**
     * Returns the file data and an assigned guest name for a guest joining a room.
     * Called by the React frontend when a guest navigates to /room/{roomId}.
     * Looks up the room record, loads the associated file, encodes its content
     * in Base64 (to safely transport arbitrary source code as JSON), and generates
     * a random display name for the guest.
     * This endpoint is intentionally public (no authentication required) so that
     * guests can join without creating an account — see SecurityConfig for the
     * /api/room/join/** permit-all rule.
     * GET /api/room/join/{roomId}
     * @param roomId the room ID extracted from the share URL
     * @return 200 OK with a JSON object containing:
     *         - "fileId":    the ID of the shared file (used for WebSocket routing)
     *         - "fileName":  the display name of the file shown in the editor header
     *         - "content":   the file's current content, Base64-encoded
     *         - "guestName": a randomly generated display name assigned to this guest
     * @throws RuntimeException if no room exists for the given roomId (results in 500;
     *                          a @ControllerAdvice mapping to 404 would be preferable)
     */
    @GetMapping("/api/room/join/{roomId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getRoomData(@PathVariable String roomId) {
        log.info("════════════════════════════════════════════════════");
        log.info("👤 GUEST JOINING ROOM");
        log.info("   Room ID: {}", roomId);

        RoomEntity room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found: " + roomId));

        FileEntity file = fileService.getFileById(room.getFileId());

        // Encode the file content as Base64 to safely embed arbitrary Java source
        // code (which may contain characters that interfere with JSON serialisation)
        // inside the JSON response body
        String encodedContent = Base64.getEncoder()
                .encodeToString(file.getContent().getBytes());

        // Assign a random guest display name for use in the active users list,
        // cursor overlays, and permission grants during the session
        String guestName = generateGuestName();

        log.info("   File ID: {}", file.getId());
        log.info("   File Name: {}", file.getName());
        log.info("   Guest Name: {}", guestName);
        log.info("════════════════════════════════════════════════════");

        return ResponseEntity.ok(Map.of(
                "fileId",    file.getId(),
                "fileName",  file.getName(),
                "content",   encodedContent,
                "guestName", guestName
        ));
    }

    /**
     * Generates a random 11-character alphanumeric room ID.
     * Uses lowercase letters and digits only (no ambiguous characters such as
     * 0/O or 1/l) to produce a URL-safe identifier. With a 36-character alphabet
     * and 11 positions the collision probability is negligible for the expected
     * number of concurrent rooms in an educational deployment.
     * @return an 11-character alphanumeric room ID string
     */
    private String generateRoomId() {
        String chars = "abcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder id = new StringBuilder(11);
        for (int i = 0; i < 11; i++) {
            id.append(chars.charAt(random.nextInt(chars.length())));
        }
        return id.toString();
    }

    /**
     * Generates a random guest display name in the format "Guest{0-9999}".
     * The numeric suffix provides enough variation to make collisions unlikely
     * in small educational sessions. The generated name is used for the session
     * lifetime only and is not persisted — guests receive a new name each time
     * they join via the room URL.
     * @return a randomly generated guest display name
     */
    private String generateGuestName() {
        int randomNum = (int) (Math.random() * 10000);
        return "Guest" + randomNum;
    }
}