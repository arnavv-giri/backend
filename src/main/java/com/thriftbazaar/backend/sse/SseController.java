package com.thriftbazaar.backend.sse;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Opens a persistent Server-Sent Events stream for the authenticated user.
 *
 * Endpoint
 * ────────
 * GET /messages/stream
 *
 * Security: requires authentication — inherited from the existing
 * SecurityConfig rule  .requestMatchers("/messages/**").authenticated()
 * The JWT is sent via the Authorization header by the Axios interceptor
 * on the first request; the browser EventSource API is extended on the
 * client side to include the token (see useMessageStream.js).
 *
 * Flow
 * ────
 * 1. Client opens GET /messages/stream with a valid JWT.
 * 2. Spring Security validates the token through JwtAuthenticationFilter.
 * 3. This handler registers an SseEmitter in SseEmitterRegistry and returns it.
 * 4. The HTTP response stays open (chunked transfer encoding).
 * 5. Whenever MessageService saves a new message it calls
 *    registry.pushToUser(recipientEmail, dto) which writes the event
 *    directly into this open response stream.
 * 6. The client's EventSource listener fires with the serialised
 *    MessageResponseDto and the UI updates without any polling.
 */
@RestController
@RequestMapping("/messages")
public class SseController {

    private final SseEmitterRegistry registry;

    public SseController(SseEmitterRegistry registry) {
        this.registry = registry;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication authentication) {
        // authentication.getName() returns the email set by JwtAuthenticationFilter
        return registry.register(authentication.getName());
    }
}
