package com.thriftbazaar.backend.dto;

/**
 * Sent by the frontend after the Razorpay modal reports a successful payment.
 *
 * All three values are provided by the Razorpay JS SDK in the
 * payment.handler callback:
 *
 *   razorpay_order_id   – the Razorpay order ID we created
 *   razorpay_payment_id – the payment ID assigned by Razorpay
 *   razorpay_signature  – HMAC-SHA256 of (orderId + "|" + paymentId)
 *                          signed with the Razorpay key secret
 *
 * The backend re-computes the signature and rejects the request if
 * it does not match — this prevents any client-side manipulation.
 */
public class PaymentVerifyRequestDto {

    private Long   orderId;             // our internal Order.id
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private String razorpaySignature;

    public Long   getOrderId()              { return orderId; }
    public void   setOrderId(Long orderId)  { this.orderId = orderId; }

    public String getRazorpayOrderId()                         { return razorpayOrderId; }
    public void   setRazorpayOrderId(String razorpayOrderId)   { this.razorpayOrderId = razorpayOrderId; }

    public String getRazorpayPaymentId()                           { return razorpayPaymentId; }
    public void   setRazorpayPaymentId(String razorpayPaymentId)   { this.razorpayPaymentId = razorpayPaymentId; }

    public String getRazorpaySignature()                           { return razorpaySignature; }
    public void   setRazorpaySignature(String razorpaySignature)   { this.razorpaySignature = razorpaySignature; }
}
