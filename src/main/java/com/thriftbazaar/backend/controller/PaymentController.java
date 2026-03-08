package com.thriftbazaar.backend.controller;

import com.thriftbazaar.backend.dto.OrderResponseDto;
import com.thriftbazaar.backend.dto.PaymentOrderResponseDto;
import com.thriftbazaar.backend.dto.PaymentVerifyRequestDto;
import com.thriftbazaar.backend.service.PaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Handles Razorpay payment operations.
 *
 * Endpoint summary
 * ─────────────────────────────────────────────────────────────────
 * POST /payments/create-order          → creates a Razorpay order  (CUSTOMER)
 * POST /payments/verify                → verifies signature, marks order PAID (CUSTOMER)
 *
 * Security: both endpoints require CUSTOMER role (enforced in SecurityConfig).
 * Amount is never taken from the client — always read from DB.
 */
@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * POST /payments/create-order
     *
     * Creates a Razorpay order for an existing DB order.
     * Request body: { "orderId": 42 }
     *
     * Returns PaymentOrderResponseDto which the frontend uses to open
     * the Razorpay checkout modal.
     */
    @PostMapping("/create-order")
    public ResponseEntity<PaymentOrderResponseDto> createOrder(
            Authentication auth,
            @RequestBody Map<String, Long> body) {

        Long orderId = body.get("orderId");
        PaymentOrderResponseDto response =
                paymentService.createPaymentOrder(auth.getName(), orderId);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /payments/verify
     *
     * Verifies the Razorpay payment signature and marks the order as PAID.
     * Request body: PaymentVerifyRequestDto
     *
     * Returns the updated OrderResponseDto with paymentStatus = "PAID".
     */
    @PostMapping("/verify")
    public ResponseEntity<OrderResponseDto> verifyPayment(
            Authentication auth,
            @RequestBody PaymentVerifyRequestDto dto) {

        OrderResponseDto updated = paymentService.verifyPayment(auth.getName(), dto);
        return ResponseEntity.ok(updated);
    }
}
