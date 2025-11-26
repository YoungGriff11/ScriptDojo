package org.scriptdojo.backend.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.scriptdojo.backend.repository.RoomRepository;
import org.scriptdojo.backend.entity.RoomEntity;
import org.scriptdojo.backend.entity.FileEntity;
import org.scriptdojo.backend.service.FileService;
import lombok.RequiredArgsConstructor;
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
public class RoomController {

    private final RoomRepository roomRepository;
    private final FileService fileService;
    private final SimpMessagingTemplate messagingTemplate;
    private final UserService userService;

    // Conor clicks "Share" → generates link
    @PostMapping("/api/room/create")
    @ResponseBody
    public CreateRoomResponse createRoom(@RequestParam Long fileId, Authentication auth) {
        String roomId = UUID.randomUUID().toString().substring(0, 12).replace("-", "");
        Long hostId = userService.getCurrentUser().getId();

        RoomEntity room = new RoomEntity(roomId, fileId, hostId);
        roomRepository.save(room);

        String url = "http://localhost:8080/room/" + roomId;
        return new CreateRoomResponse(roomId, url);
    }

    // Darren opens the link
    @GetMapping("/room/{roomId}")
    public void joinRoom(@PathVariable String roomId, HttpServletResponse response) throws IOException {
        RoomEntity room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        FileEntity file = fileService.getFileById(room.getFileId());

        String encodedContent = Base64.getEncoder()
                .encodeToString(file.getContent().getBytes(StandardCharsets.UTF_8));

        String guestUrl = "http://localhost:8080/room-guest.html"
                + "?roomId=" + roomId
                + "&fileId=" + file.getId()
                + "&fileName=" + URLEncoder.encode(file.getName(), StandardCharsets.UTF_8)
                + "&content=" + encodedContent;

        response.sendRedirect(guestUrl);
    }

    // Conor grants edit rights to Darren
    @PostMapping("/api/room/{roomId}/grant-edit")
    @ResponseBody
    public void grantEdit(@PathVariable String roomId, @RequestParam String guestName) {
        messagingTemplate.convertAndSend("/topic/room/" + roomId + "/permissions",
                Map.of("username", guestName, "canEdit", true));
    }
}
