package org.scriptdojo.backend.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.scriptdojo.backend.entity.FileEntity;
import org.scriptdojo.backend.entity.RoomEntity;
import org.scriptdojo.backend.repository.RoomRepository;
import org.scriptdojo.backend.service.FileService;
import org.scriptdojo.backend.security.CustomUserDetails;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Random;

@Controller
@RequiredArgsConstructor
@Slf4j
public class RoomController {

    private final RoomRepository roomRepository;
    private final FileService fileService;

    /**
     * Create a new collaboration room for a file
     * POST /api/room/create?fileId={fileId}
     */
    @PostMapping("/api/room/create")
    @ResponseBody
    public ResponseEntity<Map<String, String>> createRoom(
            @RequestParam Long fileId,
            Authentication auth
    ) {
        // Get current user
        CustomUserDetails user = (CustomUserDetails) auth.getPrincipal();
        Long hostId = user.getId();

        // Generate unique room ID (11 chars)
        String roomId = generateRoomId();

        // Create room entity (without builder)
        RoomEntity room = new RoomEntity();
        room.setId(roomId);
        room.setFileId(fileId);
        room.setHostId(hostId);
        room.setCreatedAt(LocalDateTime.now());

        roomRepository.save(room);

        String shareUrl = "http://localhost:8080/room/" + roomId;

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
     * Guest joins a room via share link
     * GET /room/{roomId}
     */
    @GetMapping("/room/{roomId}")
    public RedirectView joinRoom(@PathVariable String roomId) {
        log.info("════════════════════════════════════════════════════");
        log.info("👤 GUEST JOINING ROOM");
        log.info("   Room ID: {}", roomId);

        // Find room
        RoomEntity room = roomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found: " + roomId));

        // Get file
        FileEntity file = fileService.getFileById(room.getFileId());

        log.info("   File ID: {}", file.getId());
        log.info("   File Name: {}", file.getName());
        log.info("   Content Length: {} characters", file.getContent().length());

        // Encode file content as Base64 for URL
        String encodedContent = Base64.getEncoder().encodeToString(file.getContent().getBytes());

        // Build redirect URL with all data
        String redirectUrl = String.format(
                "/room-guest.html?roomId=%s&fileId=%d&fileName=%s&content=%s",
                roomId,
                file.getId(),
                file.getName(),
                encodedContent
        );

        log.info("✅ Redirecting guest to room-guest.html");
        log.info("════════════════════════════════════════════════════");

        return new RedirectView(redirectUrl);
    }

    /**
     * Generate a random 11-character room ID
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
}