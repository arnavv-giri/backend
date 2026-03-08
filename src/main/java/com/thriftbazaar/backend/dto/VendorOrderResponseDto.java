package com.thriftbazaar.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * An order as seen by a vendor — includes buyer email and only the
 * line-items that belong to this vendor's products.
 */
public class VendorOrderResponseDto {

    private Long            orderId;
    private String          orderStatus;
    private String          paymentStatus;
    private double          orderTotal;        // full order total (may contain other vendors)
    private LocalDateTime   createdAt;
    private String          deliveryAddress;
    private String          buyerEmail;
    private List<VendorOrderItemDto> items;

    public VendorOrderResponseDto(Long orderId, String orderStatus, String paymentStatus,
                                  double orderTotal, LocalDateTime createdAt,
                                  String deliveryAddress, String buyerEmail,
                                  List<VendorOrderItemDto> items) {
        this.orderId         = orderId;
        this.orderStatus     = orderStatus;
        this.paymentStatus   = paymentStatus;
        this.orderTotal      = orderTotal;
        this.createdAt       = createdAt;
        this.deliveryAddress = deliveryAddress;
        this.buyerEmail      = buyerEmail;
        this.items           = items;
    }

    public Long            getOrderId()         { return orderId; }
    public String          getOrderStatus()     { return orderStatus; }
    public String          getPaymentStatus()   { return paymentStatus; }
    public double          getOrderTotal()      { return orderTotal; }
    public LocalDateTime   getCreatedAt()       { return createdAt; }
    public String          getDeliveryAddress() { return deliveryAddress; }
    public String          getBuyerEmail()      { return buyerEmail; }
    public List<VendorOrderItemDto> getItems()  { return items; }

    // ── Inner DTO ──────────────────────────────────────────────────────────

    public static class VendorOrderItemDto {
        private Long   orderItemId;
        private Long   productId;
        private String productName;
        private double priceAtPurchase;
        private int    quantity;
        private String imageUrl;

        public VendorOrderItemDto(Long orderItemId, Long productId, String productName,
                                  double priceAtPurchase, int quantity, String imageUrl) {
            this.orderItemId     = orderItemId;
            this.productId       = productId;
            this.productName     = productName;
            this.priceAtPurchase = priceAtPurchase;
            this.quantity        = quantity;
            this.imageUrl        = imageUrl;
        }

        public Long   getOrderItemId()       { return orderItemId; }
        public Long   getProductId()         { return productId; }
        public String getProductName()       { return productName; }
        public double getPriceAtPurchase()   { return priceAtPurchase; }
        public int    getQuantity()          { return quantity; }
        public String getImageUrl()          { return imageUrl; }
    }
}
