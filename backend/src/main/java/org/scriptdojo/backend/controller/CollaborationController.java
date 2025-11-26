package org.scriptdojo.backend.controller;

import lombok.RequiredArgsConstructor;
import org.scriptdojo.backend.entity.FileEntity;
import org.scriptdojo.backend.service.dto.TextChange;
import org.scriptdojo.backend.service.dto.CursorMove;
import org.scriptdojo.backend.service.FileService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class CollaborationController {

    private final FileService fileService;

    @MessageMapping("/room/{fileId}/edit")
    @SendTo("/topic/room/{fileId}")
    public TextChange handleEdit(@DestinationVariable Long fileId, TextChange change) {
        fileService.updateFile(fileId, change.content());
        FileEntity freshFile = fileService.getFileById(fileId);
        return new TextChange(freshFile.getContent());
    }

    @MessageMapping("/room/{fileId}/cursor")
    @SendTo("/topic/room/{fileId}/cursors")
    public CursorMove handleCursor(@DestinationVariable Long fileId, CursorMove move, Authentication auth) {
        return new CursorMove(auth.getName(), move.line(), move.column());
    }
}
