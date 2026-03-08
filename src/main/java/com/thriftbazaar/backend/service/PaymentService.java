package com.thriftbazaar.backend.service;

import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.thriftbazaar.backend.dto.OrderResponseDto;
import com.thriftbazaar.backend.dto.PaymentOrderResponseDto;
import com.thriftbazaar.backend.dto.PaymentVerifyRequestDto;
import com.thriftbazaar.backend.entity.User;
import com.thriftbazaar.backend.exception.InvalidRequestException;
import com.thriftbazaar.backend.exception.ResourceNotFoundException;
import com.thriftbazaar.backend.exception.UnauthorizedActionException;
import com.thriftbazaar.backend.repository.OrderRepository;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Handles all Razorpay payment operations.
 *
 * Flow
 * ────
 * 1. Customer submits the checkout form  →  frontend calls POST /payments/create-order
 *    - We look up the existing DB order (already created with UNPAID status).
 *    - We call the Razorpay API to create a Razorpay order for that amount.
 *    - We store the razorpayOrderId on our Order row.
 *    - We return { razorpayOrderId, amount, currency, keyId, orderId } to the frontend.
 *
 * 2. Frontend opens the Razorpay checkout modal using those values.
 *    The customer completes payment inside the modal.
 *
 * 3. Razorpay calls payment.handler on success  →  frontend calls POST /payments/verify
 *    - We receive { orderId, razorpayOrderId, razorpayPaymentId, razorpaySignature }.
 *    - We re-compute HMAC-SHA256(razorpayOrderId + "|" + razorpayPaymentId, keySecret).
 *    - If the computed hex equals razorpaySignature → payment is authentic.
 *    - We mark the order PAID and store the paymentId.
 *    - We return the updated OrderResponseDto.
 *
 * Security guarantees
 * ───────────────────
 * - Amount is NEVER read from the client during verification.  We always use
 *   order.getTotalAmount() from our DB, so no amount manipulation is possible.
 * - The signature check uses the Razorpay key SECRET which never leaves the server.
 * - Only the order owner can initiate or verify payment for their own order.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final OrderRepository orderRepository;
    private final OrderService    orderService;
    private final UserService     userService;

    @Value("${razorpay.key-id}")
    private String keyId;

    @Value("${razorpay.key-secret}")
    private String keySecret;

    public PaymentService(OrderRepository orderRepository,
                          OrderService    orderService,
                          UserService     userService) {
        this.orderRepository = orderRepository;
        this.orderService    = orderService;
        this.userService     = userService;
    }

    // ─────────────────────────────────────────────────────────────────────
    // STEP 1 — Create a Razorpay order for an existing DB order
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Creates a Razorpay payment order for the given internal order ID.
     *
     * @param authenticatedEmail JWT principal — must be the order owner
     * @param internalOrderId    our DB Order.id
     * @return PaymentOrderResponseDto the frontend uses to open the modal
     */
    @Transactional
    public PaymentOrderResponseDto createPaymentOrder(String authenticatedEmail,
                                                       Long   internalOrderId) {

        com.thriftbazaar.backend.entity.Order order = orderRepository
                .findByIdWithItems(internalOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", internalOrderId));

        // Only the order owner can initiate payment
        User caller = userService.getByEmail(authenticatedEmail);
        if (!order.getCustomer().getId().equals(caller.getId())) {
            throw new UnauthorizedActionException(
                    "You do not have permission to pay for this order");
        }

        // Prevent double-payment
        if ("PAID".equals(order.getPaymentStatus())) {
            throw new InvalidRequestException("This order has already been paid");
        }

        // Amount in paise (Razorpay uses smallest currency unit)
        // We compute from DB — never trust the client amount.
        long amountInPaise = Math.round(order.getTotalAmount() * 100);

        try {
            RazorpayClient client = new RazorpayClient(keyId, keySecret);

            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount",   amountInPaise);
            orderRequest.put("currency", "INR");
            // receipt is our internal order ID, helps reconcile in the Razorpay dashboard
            orderRequest.put("receipt",  "order_" + internalOrderId);

            Order razorpayOrder = client.orders.create(orderRequest);
            String razorpayOrderId = razorpayOrder.get("id");

            // Persist the Razorpay order ID so we can validate it during verification
            order.setRazorpayOrderId(razorpayOrderId);
            orderRepository.save(order);

            log.info("Razorpay order created — internalOrderId={} razorpayOrderId={} amountPaise={}",
                    internalOrderId, razorpayOrderId, amountInPaise);

            return new PaymentOrderResponseDto(
                    razorpayOrderId,
                    amountInPaise,
                    "INR",
                    keyId,           // publishable key — safe to send to browser
                    internalOrderId
            );

        } catch (RazorpayException e) {
            log.error("Failed to create Razorpay order for internalOrderId={}", internalOrderId, e);
            throw new RuntimeException("Payment service unavailable. Please try again later.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // STEP 2 — Verify payment signature and mark order as PAID
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Verifies the Razorpay payment signature and marks the order as PAID.
     *
     * Signature algorithm (from Razorpay docs):
     *   generated_signature = HMAC_SHA256(razorpayOrderId + "|" + razorpayPaymentId,
     *                                     keySecret)
     *
     * @param authenticatedEmail JWT principal — must be the order owner
     * @param dto                payment IDs + signature from the Razorpay modal callback
     * @return updated OrderResponseDto with paymentStatus = PAID
     */
    @Transactional
    public OrderResponseDto verifyPayment(String authenticatedEmail,
                                          PaymentVerifyRequestDto dto) {

        com.thriftbazaar.backend.entity.Order order = orderRepository
                .findByIdWithItems(dto.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", dto.getOrderId()));

        // Only the order owner can verify payment
        User caller = userService.getByEmail(authenticatedEmail);
        if (!order.getCustomer().getId().equals(caller.getId())) {
            throw new UnauthorizedActionException(
                    "You do not have permission to verify payment for this order");
        }

        // Guard: the razorpayOrderId in the request must match what we stored
        if (order.getRazorpayOrderId() == null
                || !order.getRazorpayOrderId().equals(dto.getRazorpayOrderId())) {
            log.warn("Signature verification failed: razorpayOrderId mismatch — " +
                     "stored={} received={} internalOrderId={}",
                     order.getRazorpayOrderId(), dto.getRazorpayOrderId(), dto.getOrderId());
            throw new InvalidRequestException("Payment verification failed: order ID mismatch");
        }

        // Re-compute the expected signature server-side
        String expectedSignature = computeHmacSha256(
                dto.getRazorpayOrderId() + "|" + dto.getRazorpayPaymentId(),
                keySecret
        );

        if (!expectedSignature.equals(dto.getRazorpaySignature())) {
            log.warn("Signature verification failed — internalOrderId={} razorpayOrderId={}",
                    dto.getOrderId(), dto.getRazorpayOrderId());
            throw new InvalidRequestException("Payment verification failed: invalid signature");
        }

        // Signature is valid — mark the order as PAID
        order.setPaymentStatus("PAID");
        order.setRazorpayPaymentId(dto.getRazorpayPaymentId());
        // Advance fulfilment status from PENDING → PROCESSING now that payment is confirmed
        if ("PENDING".equals(order.getStatus())) {
            order.setStatus("PROCESSING");
        }
        orderRepository.save(order);

        log.info("Payment verified — internalOrderId={} razorpayPaymentId={} status now PAID/PROCESSING",
                dto.getOrderId(), dto.getRazorpayPaymentId());

        return orderService.toDto(order);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Computes HMAC-SHA256 of the given data using the provided secret,
     * and returns the result as a lowercase hex string.
     *
     * This is the exact algorithm Razorpay uses to sign webhook payloads
     * and payment callbacks.
     */
    private String computeHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HMAC-SHA256 computation failed", e);
        }
    }
}
