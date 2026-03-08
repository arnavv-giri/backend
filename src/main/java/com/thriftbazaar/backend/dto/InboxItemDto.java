package com.thriftbazaar.backend.dto;

import java.time.LocalDateTime;

/**
 * One entry in the inbox — represents the latest message
 * of a unique (product, otherUser) conversation pair.
 * Returned by GET /messages/inbox
 */
public class InboxItemDto {

    private Long          productId;
    private String        productName;
    private String        productImageUrl;   // first image, or null
    private Long          otherUserId;
    private String        otherUserEmail;
    private String        lastMessage;
    private LocalDateTime lastMessageTime;
    private int           unreadCount;

    public InboxItemDto(
            Long productId,
            String productName,
            String productImageUrl,
            Long otherUserId,
            String otherUserEmail,
            String lastMessage,
            LocalDateTime lastMessageTime,
            int unreadCount
    ) {
        this.productId       = productId;
        this.productName     = productName;
        this.productImageUrl = productImageUrl;
        this.otherUserId     = otherUserId;
        this.otherUserEmail  = otherUserEmail;
        this.lastMessage     = lastMessage;
        this.lastMessageTime = lastMessageTime;
        this.unreadCount     = unreadCount;
    }

    public Long          getProductId()       { return productId; }
    public String        getProductName()     { return productName; }
    public String        getProductImageUrl() { return productImageUrl; }
    public Long          getOtherUserId()     { return otherUserId; }
    public String        getOtherUserEmail()  { return otherUserEmail; }
    public String        getLastMessage()     { return lastMessage; }
    public LocalDateTime getLastMessageTime() { return lastMessageTime; }
    public int           getUnreadCount()     { return unreadCount; }
}
