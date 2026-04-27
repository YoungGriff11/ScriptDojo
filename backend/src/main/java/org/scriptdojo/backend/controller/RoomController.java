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

@Controller
@RequiredArgsConstructor
@Slf4j
public class RoomController {

    private final RoomRepository roomRepository;
    private final FileService fileService;

    @Value("${app.base-url:http://localhost:8080}")
    private String baseUrl;

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
        CustomUserDetails user = (CustomUserDetails) auth.getPrincipal();
        Long hostId = user.getId();

        String roomId = generateRoomId();

        RoomEntity room = new RoomEntity();
        room.setId(roomId);
        room.setFileId(fileId);
        room.setHostId(hostId);
        room.setCreatedAt(LocalDateTime.now());

        roomRepository.save(room);

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
     * Guest fetches room data via React frontend
     * GET /api/room/join/{roomId}
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

        String encodedContent = Base64.getEncoder()
                .encodeToString(file.getContent().getBytes());

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

    /**
     * Generate a random guest name
     */
    private String generateGuestName() {
        int randomNum = (int) (Math.random() * 10000);
        return "Guest" + randomNum;
    }
}