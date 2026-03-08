package com.thriftbazaar.backend.sse;

import com.thriftbazaar.backend.dto.MessageResponseDto;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central registry of live SSE connections, keyed by user email.
 *
 * Design notes
 * ────────────
 * One user can have multiple browser tabs open — we keep a List per email.
 * All mutations are synchronised on the per-email list to avoid
 * ConcurrentModificationException during iteration.
 * ConcurrentHashMap is used for the outer map so different users
 * never block each other.
 * Dead emitters (completed / timed-out / errored) are removed lazily
 * inside push() and eagerly via onCompletion / onTimeout / onError callbacks.
 */
@Component
public class SseEmitterRegistry {

    // email -> list of live emitters (one per open browser tab / connection)
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    // ── Register ──────────────────────────────────────────────────────────

    /**
     * Creates a new SseEmitter for userEmail, registers cleanup callbacks,
     * and returns the emitter to the controller to stream into the response.
     *
     * Timeout: 30 minutes. The browser EventSource API reconnects automatically
     * when a connection drops or times out — no client-side reconnect logic needed.
     */
    public SseEmitter register(String userEmail) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);

        List<SseEmitter> list = emitters.computeIfAbsent(
                userEmail, k -> new ArrayList<>());

        synchronized (list) {
            list.add(emitter);
        }

        Runnable cleanup = () -> remove(userEmail, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        return emitter;
    }

    // ── Push ──────────────────────────────────────────────────────────────

    /**
     * Sends a MessageResponseDto as an SSE event named "message" to every
     * active emitter registered for recipientEmail.
     *
     * Dead emitters found during send are collected and removed afterwards
     * so we never modify the list while iterating it.
     */
    public void pushToUser(String recipientEmail, MessageResponseDto dto) {
        List<SseEmitter> list = emitters.get(recipientEmail);
        if (list == null) return;

        List<SseEmitter> dead = new ArrayList<>();

        synchronized (list) {
            for (SseEmitter emitter : list) {
                try {
                    emitter.send(
                            SseEmitter.event()
                                    .name("message")
                                    .data(dto)
                    );
                } catch (IOException | IllegalStateException e) {
                    // Emitter was closed between the null-check and the send
                    dead.add(emitter);
                }
            }
            list.removeAll(dead);
        }
    }

    // ── Internal cleanup ──────────────────────────────────────────────────

    private void remove(String userEmail, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(userEmail);
        if (list == null) return;
        synchronized (list) {
            list.remove(emitter);
        }
    }
}
