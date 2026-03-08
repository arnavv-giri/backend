package com.thriftbazaar.backend.service;

import com.thriftbazaar.backend.dto.InboxItemDto;
import com.thriftbazaar.backend.dto.MessageResponseDto;
import com.thriftbazaar.backend.dto.SendMessageRequestDto;
import com.thriftbazaar.backend.entity.Message;
import com.thriftbazaar.backend.entity.Product;
import com.thriftbazaar.backend.entity.User;
import com.thriftbazaar.backend.exception.InvalidRequestException;
import com.thriftbazaar.backend.exception.ResourceNotFoundException;
import com.thriftbazaar.backend.exception.UnauthorizedActionException;
import com.thriftbazaar.backend.repository.MessageRepository;
import com.thriftbazaar.backend.repository.ProductRepository;
import com.thriftbazaar.backend.repository.UserRepository;
import com.thriftbazaar.backend.sse.SseEmitterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MessageService {

    private static final Logger log = LoggerFactory.getLogger(MessageService.class);

    private final MessageRepository  messageRepository;
    private final ProductRepository  productRepository;
    private final UserRepository     userRepository;
    private final UserService        userService;
    private final SseEmitterRegistry sseRegistry;

    public MessageService(
            MessageRepository  messageRepository,
            ProductRepository  productRepository,
            UserRepository     userRepository,
            UserService        userService,
            SseEmitterRegistry sseRegistry
    ) {
        this.messageRepository = messageRepository;
        this.productRepository = productRepository;
        this.userRepository    = userRepository;
        this.userService       = userService;
        this.sseRegistry       = sseRegistry;
    }

    // ─────────────────────────────────────────────────────────────────────
    // SEND MESSAGE
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Persists a new message from the authenticated user to a receiver
     * about a specific product.
     *
     * Business rules:
     *  - Content must not be blank.
     *  - Content must not exceed 2000 characters.
     *  - Product must exist.
     *  - Receiver must exist.
     *  - Sender cannot message themselves.
     */
    @Transactional
    public MessageResponseDto sendMessage(String senderEmail, SendMessageRequestDto dto) {

        if (dto.getContent() == null || dto.getContent().isBlank()) {
            throw new InvalidRequestException("Message content cannot be empty");
        }
        if (dto.getContent().length() > 2000) {
            throw new InvalidRequestException("Message cannot exceed 2000 characters");
        }
        if (dto.getProductId() == null) {
            throw new InvalidRequestException("Product ID is required");
        }
        if (dto.getReceiverId() == null) {
            throw new InvalidRequestException("Receiver ID is required");
        }

        User sender = userService.getByEmail(senderEmail);

        User receiver = userRepository.findById(dto.getReceiverId())
                .orElseThrow(() -> new ResourceNotFoundException("User", dto.getReceiverId()));

        if (sender.getId().equals(receiver.getId())) {
            throw new InvalidRequestException("You cannot send a message to yourself");
        }

        Product product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", dto.getProductId()));

        Message message = new Message();
        message.setSender(sender);
        message.setReceiver(receiver);
        message.setProduct(product);
        message.setContent(dto.getContent().trim());
        message.setTimestamp(LocalDateTime.now());
        message.setRead(false);

        MessageResponseDto saved = toDto(messageRepository.save(message));
        log.info("Message sent — messageId={} productId={} senderId={} receiverId={}",
                saved.getId(), dto.getProductId(), sender.getId(), receiver.getId());

        // ── Real-time delivery ────────────────────────────────────────────
        // Fire-and-forget push to both participants' SSE streams.
        sseRegistry.pushToUser(receiver.getEmail(), saved);
        sseRegistry.pushToUser(sender.getEmail(),   saved);

        return saved;
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET CONVERSATION
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns all messages between the authenticated user and another user
     * about a specific product, ordered by timestamp ascending.
     *
     * Also marks incoming unread messages as read.
     *
     * The repository query uses JOIN FETCH on sender and receiver so that
     * toDto() can read their emails without triggering additional SELECT
     * statements (one per message in the previous implementation).
     */
    @Transactional
    public List<MessageResponseDto> getConversation(
            String currentUserEmail,
            Long   productId,
            Long   otherUserId
    ) {
        User currentUser = userService.getByEmail(currentUserEmail);

        if (!userRepository.existsById(otherUserId)) {
            throw new ResourceNotFoundException("User", otherUserId);
        }

        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product", productId);
        }

        // Mark messages sent to us by the other user as read
        messageRepository.markConversationRead(productId, currentUser.getId(), otherUserId);

        return messageRepository
                .findConversation(productId, currentUser.getId(), otherUserId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET INBOX
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns one entry per unique (product, otherUser) conversation.
     *
     * Performance fix: N+1 elimination
     * ─────────────────────────────────
     * Previously this method called countUnread() once per inbox row inside
     * a stream().map() — issuing up to N extra SELECT COUNT queries where N
     * is the number of conversations.  For a user with 50 conversations that
     * was 51 database round-trips per inbox load.
     *
     * Now:
     *   1. findLatestPerConversation() issues ONE query and returns inbox rows
     *      with sender/receiver/product all JOIN FETCHed (no lazy loads).
     *   2. countAllUnreadGrouped() issues ONE aggregated query returning all
     *      (productId, senderId, count) tuples for this user.
     *   3. We build a lookup map from those tuples in O(n) Java memory.
     *   4. Each inbox row does a single O(1) map lookup.
     *
     * Total: always 2 database queries regardless of inbox size.
     */
    public List<InboxItemDto> getInbox(String currentUserEmail) {

        User currentUser = userService.getByEmail(currentUserEmail);

        // Query 1 — latest message per conversation (fully fetched, no lazy proxies)
        List<Message> latestMessages =
                messageRepository.findLatestPerConversation(currentUser.getId());

        // Query 2 — all unread counts for this user in one aggregated query
        //           Result rows: Object[] { productId, senderId, count }
        List<Object[]> unreadRows =
                messageRepository.countAllUnreadGrouped(currentUser.getId());

        // Build lookup: "productId:senderId" → unread count
        Map<String, Long> unreadMap = new HashMap<>();
        for (Object[] row : unreadRows) {
            Long productId = (Long) row[0];
            Long senderId  = (Long) row[1];
            Long count     = (Long) row[2];
            unreadMap.put(productId + ":" + senderId, count);
        }

        return latestMessages.stream()
                .map(msg -> {
                    User otherUser = msg.getSender().getId().equals(currentUser.getId())
                            ? msg.getReceiver()
                            : msg.getSender();

                    // O(1) map lookup — no database call
                    long unread = unreadMap.getOrDefault(
                            msg.getProduct().getId() + ":" + otherUser.getId(), 0L);

                    // product.images is already JOIN FETCHed — no lazy load here
                    String imageUrl = null;
                    if (msg.getProduct().getImages() != null
                            && !msg.getProduct().getImages().isEmpty()) {
                        imageUrl = msg.getProduct().getImages().get(0).getImageUrl();
                    }

                    return new InboxItemDto(
                            msg.getProduct().getId(),
                            msg.getProduct().getName(),
                            imageUrl,
                            otherUser.getId(),
                            otherUser.getEmail(),
                            msg.getContent(),
                            msg.getTimestamp(),
                            (int) unread
                    );
                })
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────
    // MARK MESSAGE READ
    // ─────────────────────────────────────────────────────────────────────

    @Transactional
    public void markMessageRead(String currentUserEmail, Long messageId) {

        User currentUser = userService.getByEmail(currentUserEmail);

        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message", messageId));

        if (!message.getReceiver().getId().equals(currentUser.getId())) {
            throw new UnauthorizedActionException(
                    "You can only mark messages addressed to you as read");
        }

        message.setRead(true);
        messageRepository.save(message);
    }

    // ─────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────

    /** Maps a Message entity to its response DTO. */
    private MessageResponseDto toDto(Message msg) {
        return new MessageResponseDto(
                msg.getId(),
                msg.getSender().getId(),
                msg.getSender().getEmail(),
                msg.getReceiver().getId(),
                msg.getReceiver().getEmail(),
                msg.getProduct().getId(),
                msg.getContent(),
                msg.getTimestamp(),
                msg.isRead()
        );
    }
}
