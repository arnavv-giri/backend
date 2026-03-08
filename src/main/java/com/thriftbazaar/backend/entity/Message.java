package com.thriftbazaar.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Represents a single chat message between a buyer and a seller
 * about a specific product.
 *
 * A conversation is uniquely identified by the triple:
 *   (product_id, buyer_id, seller_id)
 *
 * Indexes on sender_id, receiver_id, and product_id ensure that
 * conversation lookups and inbox queries are fast even at scale.
 */
@Entity
@Table(
    name = "messages",
    indexes = {
        @Index(name = "idx_message_sender",   columnList = "sender_id"),
        @Index(name = "idx_message_receiver", columnList = "receiver_id"),
        @Index(name = "idx_message_product",  columnList = "product_id")
    }
)
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, length = 2000)
    private String content;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false)
    private boolean read = false;

    // ── Getters & setters ─────────────────────────────────────────────────

    public Long getId()                  { return id; }

    public User getSender()              { return sender; }
    public void setSender(User sender)   { this.sender = sender; }

    public User getReceiver()            { return receiver; }
    public void setReceiver(User receiver) { this.receiver = receiver; }

    public Product getProduct()          { return product; }
    public void setProduct(Product product) { this.product = product; }

    public String getContent()           { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDateTime getTimestamp()  { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public boolean isRead()              { return read; }
    public void setRead(boolean read)    { this.read = read; }
}
