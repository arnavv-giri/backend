package com.thriftbazaar.backend.dto;

import java.time.LocalDateTime;

/**
 * Represents a single message in a conversation thread.
 * Returned by GET /messages/conversation/{productId}/{userId}
 */
public class MessageResponseDto {

    private Long          id;
    private Long          senderId;
    private String        senderEmail;
    private Long          receiverId;
    private String        receiverEmail;
    private Long          productId;
    private String        content;
    private LocalDateTime timestamp;
    private boolean       read;

    public MessageResponseDto(
            Long id,
            Long senderId,
            String senderEmail,
            Long receiverId,
            String receiverEmail,
            Long productId,
            String content,
            LocalDateTime timestamp,
            boolean read
    ) {
        this.id            = id;
        this.senderId      = senderId;
        this.senderEmail   = senderEmail;
        this.receiverId    = receiverId;
        this.receiverEmail = receiverEmail;
        this.productId     = productId;
        this.content       = content;
        this.timestamp     = timestamp;
        this.read          = read;
    }

    public Long          getId()            { return id; }
    public Long          getSenderId()      { return senderId; }
    public String        getSenderEmail()   { return senderEmail; }
    public Long          getReceiverId()    { return receiverId; }
    public String        getReceiverEmail() { return receiverEmail; }
    public Long          getProductId()     { return productId; }
    public String        getContent()       { return content; }
    public LocalDateTime getTimestamp()     { return timestamp; }
    public boolean       isRead()           { return read; }
}
