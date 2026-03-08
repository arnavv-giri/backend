package com.thriftbazaar.backend.controller;

import com.thriftbazaar.backend.dto.InboxItemDto;
import com.thriftbazaar.backend.dto.MessageResponseDto;
import com.thriftbazaar.backend.dto.SendMessageRequestDto;
import com.thriftbazaar.backend.service.MessageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Handles marketplace messaging between buyers and sellers.
 *
 * All endpoints require authentication — enforced in SecurityConfig.
 *
 * Responsibilities:
 *  - Extract authenticated email from Spring Security.
 *  - Forward to MessageService.
 *  - Return ResponseEntity.
 *
 * No business logic. No repository access.
 */
@RestController
@RequestMapping("/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    // POST /messages — Send a message to a seller (or reply from seller to buyer)
    @PostMapping
    public ResponseEntity<MessageResponseDto> sendMessage(
            @RequestBody SendMessageRequestDto dto,
            Authentication authentication
    ) {
        MessageResponseDto sent = messageService.sendMessage(
                authentication.getName(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(sent);
    }

    // GET /messages/conversation/{productId}/{userId}
    // Returns the full thread between the caller and the given user for that product
    @GetMapping("/conversation/{productId}/{userId}")
    public ResponseEntity<List<MessageResponseDto>> getConversation(
            @PathVariable Long productId,
            @PathVariable Long userId,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                messageService.getConversation(authentication.getName(), productId, userId));
    }

    // GET /messages/inbox — Returns inbox (one entry per unique conversation)
    @GetMapping("/inbox")
    public ResponseEntity<List<InboxItemDto>> getInbox(Authentication authentication) {
        return ResponseEntity.ok(
                messageService.getInbox(authentication.getName()));
    }

    // PATCH /messages/{messageId}/read — Mark a single message as read
    @PatchMapping("/{messageId}/read")
    public ResponseEntity<Void> markRead(
            @PathVariable Long messageId,
            Authentication authentication
    ) {
        messageService.markMessageRead(authentication.getName(), messageId);
        return ResponseEntity.noContent().build();
    }
}
