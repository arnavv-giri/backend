package com.thriftbazaar.backend.service;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.thriftbazaar.backend.dto.OrderResponseDto;
import com.thriftbazaar.backend.dto.PaymentOrderResponseDto;
import com.thriftbazaar.backend.dto.PaymentVerifyRequestDto;
import com.thriftbazaar.backend.entity.User;
import com.thriftbazaar.backend.exception.InvalidRequestException;
import com.thriftbazaar.backend.exception.PaymentServiceException;
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
 * RazorpayClient singleton
 * ────────────────────────
 * RazorpayClient is constructed ONCE at bean-initialisation time rather than
 * per request.  Creating it per-request caused an OkHttp class-loading conflict
 * at runtime when cloudinary-http5 and razorpay-java 1.4.6 are both on the
 * classpath, resulting in a RazorpayException on every payment attempt and the
 * frontend seeing "An unexpected error occurred" (the generic 500 handler message).
 *
 * Security guarantees
 * ───────────────────
 * - Amount is NEVER read from the client during verification — always from the DB.
 * - The signature check uses the Razorpay key SECRET which never leaves the server.
 * - Only the order owner can initiate or verify payment for their own order.
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final OrderRepository orderRepository;
    private final OrderService    orderService;
    private final UserService     userService;

    // Both kept as fields — keyId for the DTO response, keySecret for HMAC verification.
    private final String         keyId;
    private final String         keySecret;
    private final RazorpayClient razorpayClient;   // singleton — built once at startup

    public PaymentService(
            OrderRepository orderRepository,
            OrderService    orderService,
            UserService     userService,
            @Value("${razorpay.key-id}")     String keyId,
            @Value("${razorpay.key-secret}") String keySecret
    ) {
        this.orderRepository = orderRepository;
        this.orderService    = orderService;
        this.userService     = userService;
        this.keyId           = keyId;
        this.keySecret       = keySecret;

        // Build the Razorpay HTTP client exactly once during Spring context startup.
        // Constructing it per-request triggered an OkHttp initialisation race /
        // class-loading conflict with cloudinary-http5.
        try {
            this.razorpayClient = new RazorpayClient(keyId, keySecret);
            log.info("RazorpayClient initialised — key prefix: {}",
                    keyId.substring(0, Math.min(keyId.length(), 12)));
        } catch (RazorpayException e) {
            // Fail fast: bad keys must not silently cause every payment to fail.
            log.error("FATAL: Could not initialise RazorpayClient — " +
                      "check RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET", e);
            throw new IllegalStateException(
                    "Failed to initialise Razorpay SDK — " +
                    "verify RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET are correct.", e);
        }
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

        // Amount in paise (Razorpay: 1 INR = 100 paise)
        // Always computed from DB — never trust a client-supplied amount.
        long amountInPaise = Math.round(order.getTotalAmount() * 100);

        log.info("Creating Razorpay order — internalOrderId={} amountPaise={} customer={}",
                internalOrderId, amountInPaise, authenticatedEmail);

        try {
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount",   amountInPaise);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt",  "order_" + internalOrderId);

            com.razorpay.Order razorpayOrder = razorpayClient.orders.create(orderRequest);
            String razorpayOrderId = razorpayOrder.get("id");

            if (razorpayOrderId == null || razorpayOrderId.isBlank()) {
                log.error("Razorpay returned order with no ID — full response: {}", razorpayOrder);
                throw new PaymentServiceException(
                        "Payment gateway returned an invalid response. Please try again.");
            }

            // Persist so we can validate during verification
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

        } catch (PaymentServiceException e) {
            throw e;   // already the right type — let it propagate
        } catch (RazorpayException e) {
            log.error("Razorpay order creation failed — internalOrderId={} error={}",
                    internalOrderId, e.getMessage(), e);
            throw new PaymentServiceException(
                    "Payment service is currently unavailable. Please try again in a moment.");
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // STEP 2 — Verify payment signature and mark order as PAID
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Verifies the Razorpay payment signature and marks the order as PAID.
     *
     * Signature algorithm (from Razorpay docs):
     *   generated_signature = HMAC_SHA256(razorpayOrderId + "|" + razorpayPaymentId, keySecret)
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

        // The razorpayOrderId from the callback must match what we stored at create-order time
        if (order.getRazorpayOrderId() == null
                || !order.getRazorpayOrderId().equals(dto.getRazorpayOrderId())) {
            log.warn("Signature check failed: razorpayOrderId mismatch — " +
                     "stored={} received={} internalOrderId={}",
                     order.getRazorpayOrderId(), dto.getRazorpayOrderId(), dto.getOrderId());
            throw new InvalidRequestException("Payment verification failed: order ID mismatch");
        }

        // Re-compute the expected HMAC-SHA256 server-side
        String expectedSignature = computeHmacSha256(
                dto.getRazorpayOrderId() + "|" + dto.getRazorpayPaymentId(),
                keySecret
        );

        if (!expectedSignature.equals(dto.getRazorpaySignature())) {
            log.warn("HMAC signature mismatch — internalOrderId={} razorpayOrderId={} " +
                     "paymentId={} — possible tampered callback",
                    dto.getOrderId(), dto.getRazorpayOrderId(), dto.getRazorpayPaymentId());
            throw new InvalidRequestException("Payment verification failed: invalid signature");
        }

        // Signature valid — persist PAID status
        order.setPaymentStatus("PAID");
        order.setRazorpayPaymentId(dto.getRazorpayPaymentId());
        if ("PENDING".equals(order.getStatus())) {
            order.setStatus("PROCESSING");
        }
        orderRepository.save(order);

        log.info("Payment verified — internalOrderId={} razorpayPaymentId={} " +
                 "paymentStatus=PAID fulfilmentStatus={}",
                dto.getOrderId(), dto.getRazorpayPaymentId(), order.getStatus());

        return orderService.toDto(order);
    }

    // ─────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Computes HMAC-SHA256 of the given data using the provided secret,
     * returns the result as a lowercase hex string.
     *
     * This is the exact algorithm Razorpay uses to sign payment callbacks.
     */
    private String computeHmacSha256(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HMAC-SHA256 computation failed", e);
        }
    }
}
