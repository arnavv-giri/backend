package com.thriftbazaar.backend.dto;

/**
 * Returned after successfully creating a Razorpay order.
 *
 * The frontend needs:
 *   - razorpayOrderId  → passed to the Razorpay checkout modal as `order_id`
 *   - amount           → displayed to the user (in rupees)
 *   - currency         → always "INR"
 *   - keyId            → the publishable Razorpay key (safe to expose to the browser)
 *   - orderId          → our internal DB order ID, so the frontend can call verify
 */
public class PaymentOrderResponseDto {

    private String razorpayOrderId;
    private long   amount;          // in paise (₹1 = 100 paise)
    private String currency;
    private String keyId;           // rzp_test_... or rzp_live_...
    private Long   orderId;         // our internal Order.id

    public PaymentOrderResponseDto(String razorpayOrderId, long amount,
                                   String currency, String keyId, Long orderId) {
        this.razorpayOrderId = razorpayOrderId;
        this.amount          = amount;
        this.currency        = currency;
        this.keyId           = keyId;
        this.orderId         = orderId;
    }

    public String getRazorpayOrderId() { return razorpayOrderId; }
    public long   getAmount()          { return amount; }
    public String getCurrency()        { return currency; }
    public String getKeyId()           { return keyId; }
    public Long   getOrderId()         { return orderId; }
}
