package org.scriptdojo.backend.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.scriptdojo.backend.repository.RoomRepository;
import org.scriptdojo.backend.entity.RoomEntity;
import org.scriptdojo.backend.entity.FileEntity;
import org.scriptdojo.backend.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.scriptdojo.backend.service.dto.CreateRoomResponse;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.scriptdojo.backend.service.UserService;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Controller
@RequiredArgsConstructor
@Slf4j
public class RoomController {

    private final RoomRepository roomRepository;
    private final FileService fileService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserService userService;

    /**
     * Host creates a shareable room link
     */
    @PostMapping("/api/room/create")
    @ResponseBody
    public CreateRoomResponse createRoom(@RequestParam Long fileId, Authentication auth) {
        String roomId = UUID.randomUUID().toString().substring(0, 12).replace("-", "");
        Long hostId = userService.getCurrentUser().getId();

        RoomEntity room = new RoomEntity(roomId, fileId, hostId);
        roomRepository.save(room);

        String url = "http://localhost:8080/room/" + roomId;

        log.info("════════════════════════════════════════════════════");
        log.info("🔗 ROOM CREATED");
        log.info("   Room ID: {}", roomId);
        log.info("   File ID: {}", fileId);
        log.info("   Host ID: {}", hostId);
        log.info("   Share URL: {}", url);
        log.info("════════════════════════════════════════════════════");

        return new CreateRoomResponse(roomId, url);
    }

    /**
     * Guest joins via share link - redirects to room-guest.html with file data
     */
    @GetMapping("/room/{roomId}")
    public void joinRoom(@PathVariable String roomId, HttpServletResponse response) throws IOException {
        log.info("════════════════════════════════════════════════════");
        log.info("👤 GUEST JOINING ROOM");
        log.info("   Room ID: {}", roomId);

        RoomEntity room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        // Get FRESH file content from database
        FileEntity file = fileService.getFileById(room.getFileId());

        log.info("   File ID: {}", file.getId());
        log.info("   File Name: {}", file.getName());
        log.info("   Content Length: {} characters", file.getContent().length());

        // Encode content as Base64 for URL transport
        String encodedContent = Base64.getEncoder()
                .encodeToString(file.getContent().getBytes(StandardCharsets.UTF_8));

        String guestUrl = "http://localhost:8080/room-guest.html"
                + "?roomId=" + roomId
                + "&fileId=" + file.getId()
                + "&fileName=" + URLEncoder.encode(file.getName(), StandardCharsets.UTF_8)
                + "&content=" + encodedContent;

        log.info("✅ Redirecting guest to room-guest.html");
        log.info("════════════════════════════════════════════════════");

        response.sendRedirect(guestUrl);
    }

    /**
     * Host grants edit permission to guest
     */
    @PostMapping("/api/room/{roomId}/grant-edit")
    @ResponseBody
    public void grantEdit(@PathVariable String roomId, @RequestParam String guestName) {
        log.info("🔓 Granting EDIT permission to '{}' in room {}", guestName, roomId);

        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/permissions",
                Map.of("username", guestName, "canEdit", true));
    }
}