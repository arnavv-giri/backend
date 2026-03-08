package com.thriftbazaar.backend.repository;

import com.thriftbazaar.backend.entity.Message;
import com.thriftbazaar.backend.entity.Product;
import com.thriftbazaar.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

    /**
     * Returns all messages in a conversation between two users about one product,
     * ordered oldest → newest.
     *
     * JOIN FETCH sender and receiver so toDto() can read their emails without
     * triggering N+1 proxy loads (one extra SELECT per message otherwise).
     * For a conversation with 200 messages this reduces ~400 extra queries to 0.
     */
    @Query("""
        SELECT m FROM Message m
        JOIN FETCH m.sender
        JOIN FETCH m.receiver
        WHERE m.product.id = :productId
          AND (
              (m.sender.id = :user1Id AND m.receiver.id = :user2Id)
           OR (m.sender.id = :user2Id AND m.receiver.id = :user1Id)
          )
        ORDER BY m.timestamp ASC
    """)
    List<Message> findConversation(
            @Param("productId") Long productId,
            @Param("user1Id")   Long user1Id,
            @Param("user2Id")   Long user2Id
    );

    /**
     * Inbox query: returns the latest message per (product, otherUser) pair.
     *
     * JOIN FETCH sender, receiver, and product so the service layer can build
     * InboxItemDto entries without any additional queries.
     *
     * Also fetches product.images so the first image URL is available; images
     * is a @OneToMany collection — Hibernate handles the join correctly here
     * because we select the message (not the product) as the root.
     *
     * Performance notes
     * ─────────────────
     * The sub-query groups by (product_id, normalised-other-user-id) and selects
     * MAX(id) as a proxy for the latest message.  Using MAX(id) is reliable
     * because IDs are auto-increment — the highest ID in a group is always the
     * most recently inserted row.  This avoids a GROUP BY on a non-indexed
     * timestamp column.
     *
     * The outer query then fetches only those N rows (one per conversation)
     * rather than all messages.
     */
    @Query("""
        SELECT m FROM Message m
        JOIN FETCH m.sender
        JOIN FETCH m.receiver
        JOIN FETCH m.product p
        LEFT JOIN FETCH p.images
        WHERE m.id IN (
            SELECT MAX(m2.id)
            FROM Message m2
            WHERE m2.sender.id = :userId OR m2.receiver.id = :userId
            GROUP BY m2.product.id,
                     CASE WHEN m2.sender.id = :userId
                          THEN m2.receiver.id
                          ELSE m2.sender.id
                     END
        )
        ORDER BY m.timestamp DESC
    """)
    List<Message> findLatestPerConversation(@Param("userId") Long userId);

    /**
     * Counts unread messages sent TO this user by the other user for a product.
     * Used to populate the unread badge on each inbox row.
     *
     * This query is called once per inbox row in the service layer.  The N+1
     * pattern is eliminated by replacing it with a single aggregated query
     * (see countAllUnreadGrouped below) — this method is kept for targeted
     * single-conversation use (e.g. after opening a conversation).
     */
    @Query("""
        SELECT COUNT(m) FROM Message m
        WHERE m.product.id   = :productId
          AND m.receiver.id  = :receiverId
          AND m.sender.id    = :senderId
          AND m.read = false
    """)
    int countUnread(
            @Param("productId")  Long productId,
            @Param("receiverId") Long receiverId,
            @Param("senderId")   Long senderId
    );

    /**
     * Returns the total unread count across ALL conversations for a given user,
     * grouped by (productId, senderId).
     *
     * This replaces the N+1 loop in MessageService.getInbox() where
     * countUnread() was called once per inbox row.  Instead, one query
     * fetches all unread counts and the service joins them in memory.
     *
     * Result shape: Object[] { productId (Long), senderId (Long), count (Long) }
     */
    @Query("""
        SELECT m.product.id, m.sender.id, COUNT(m)
        FROM Message m
        WHERE m.receiver.id = :receiverId
          AND m.read = false
        GROUP BY m.product.id, m.sender.id
    """)
    List<Object[]> countAllUnreadGrouped(@Param("receiverId") Long receiverId);

    /**
     * Marks all messages sent by otherUser to currentUser for a product as read.
     * Called when a user opens a conversation.
     */
    @Modifying
    @Query("""
        UPDATE Message m
        SET m.read = true
        WHERE m.product.id   = :productId
          AND m.receiver.id  = :currentUserId
          AND m.sender.id    = :otherUserId
          AND m.read = false
    """)
    void markConversationRead(
            @Param("productId")     Long productId,
            @Param("currentUserId") Long currentUserId,
            @Param("otherUserId")   Long otherUserId
    );
}
