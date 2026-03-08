package com.thriftbazaar.backend.controller;

import com.thriftbazaar.backend.dto.CheckoutRequestDto;
import com.thriftbazaar.backend.dto.OrderResponseDto;
import com.thriftbazaar.backend.dto.VendorOrderResponseDto;
import com.thriftbazaar.backend.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for order operations.
 *
 * All security rules are enforced in SecurityConfig; the controller
 * trusts that the caller has already been authenticated and
 * authorised before reaching this layer.
 *
 * Endpoint summary
 * ────────────────────────────────────────────────────────────────
 * POST   /orders/checkout          → place an order  (CUSTOMER)
 * GET    /orders                   → my orders list  (CUSTOMER)
 * GET    /orders/{id}              → single order    (CUSTOMER who owns it, or ADMIN)
 * PUT    /orders/{id}/status       → update status   (ADMIN or VENDOR)
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // ── POST /orders/checkout ─────────────────────────────────────────────

    /**
     * Places a new order from the customer's cart.
     * Returns 201 Created with the persisted OrderResponseDto.
     */
    @PostMapping("/checkout")
    public ResponseEntity<OrderResponseDto> checkout(
            Authentication auth,
            @RequestBody CheckoutRequestDto request) {

        OrderResponseDto order = orderService.checkout(auth.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    // ── GET /orders ───────────────────────────────────────────────────────

    /**
     * Returns all orders belonging to the authenticated customer,
     * ordered newest-first.
     */
    @GetMapping
    public ResponseEntity<List<OrderResponseDto>> getMyOrders(Authentication auth) {

        return ResponseEntity.ok(orderService.getMyOrders(auth.getName()));
    }

    // ── GET /orders/{id} ──────────────────────────────────────────────────

    /**
     * Returns a single order by ID.
     * Throws 403 if the caller does not own the order (and is not ADMIN).
     */
    @GetMapping("/{id}")
    public ResponseEntity<OrderResponseDto> getOrder(
            Authentication auth,
            @PathVariable Long id) {

        return ResponseEntity.ok(orderService.getOrderById(auth.getName(), id));
    }

    // ── GET /orders/vendor ────────────────────────────────────────────────

    @GetMapping("/vendor")
    public ResponseEntity<List<VendorOrderResponseDto>> getVendorOrders(Authentication auth) {
        return ResponseEntity.ok(orderService.getVendorOrders(auth.getName()));
    }

    // ── PUT /orders/{id}/status ───────────────────────────────────────────

    /**
     * Updates the status of an order.
     * Request body: { "status": "SHIPPED" }
     * Accessible by ADMIN or VENDOR (enforced in SecurityConfig).
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<OrderResponseDto> updateStatus(
            @PathVariable Long id,
            @RequestBody Map<String, String> body) {

        String newStatus = body.get("status");
        return ResponseEntity.ok(orderService.updateStatus(id, newStatus));
    }

    // ── POST /orders/{id}/cancel ──────────────────────────────────────────

    /**
     * Allows the order owner (CUSTOMER) to cancel their own order.
     * Only cancellable when status is PENDING or PROCESSING.
     * SecurityConfig allows CUSTOMER role via the /orders/* wildcard rule.
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<OrderResponseDto> cancelOrder(
            Authentication auth,
            @PathVariable Long id) {

        return ResponseEntity.ok(orderService.cancelOrder(auth.getName(), id));
    }
}
