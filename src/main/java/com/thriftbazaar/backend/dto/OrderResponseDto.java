package com.thriftbazaar.backend.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Returned to the frontend for both the checkout confirmation and the orders list.
 * Includes payment status so the UI can distinguish paid/unpaid orders.
 */
public class OrderResponseDto {

    private Long            id;
    private String          status;
    private String          paymentStatus;      // UNPAID | PAID
    private String          razorpayOrderId;    // needed by the frontend to open Razorpay modal
    private double          totalAmount;
    private LocalDateTime   createdAt;
    private String          deliveryAddress;
    private List<OrderItemResponseDto> items;

    // ── Constructor ───────────────────────────────────────────────────────

    public OrderResponseDto(
            Long id,
            String status,
            String paymentStatus,
            String razorpayOrderId,
            double totalAmount,
            LocalDateTime createdAt,
            String deliveryAddress,
            List<OrderItemResponseDto> items) {

        this.id              = id;
        this.status          = status;
        this.paymentStatus   = paymentStatus;
        this.razorpayOrderId = razorpayOrderId;
        this.totalAmount     = totalAmount;
        this.createdAt       = createdAt;
        this.deliveryAddress = deliveryAddress;
        this.items           = items;
    }

    // ── Getters ───────────────────────────────────────────────────────────

    public Long            getId()              { return id; }
    public String          getStatus()          { return status; }
    public String          getPaymentStatus()   { return paymentStatus; }
    public String          getRazorpayOrderId() { return razorpayOrderId; }
    public double          getTotalAmount()     { return totalAmount; }
    public LocalDateTime   getCreatedAt()       { return createdAt; }
    public String          getDeliveryAddress() { return deliveryAddress; }
    public List<OrderItemResponseDto> getItems(){ return items; }

    // ── Inner DTO for each line item ──────────────────────────────────────

    public static class OrderItemResponseDto {

        private Long   id;
        private Long   productId;
        private String productName;
        private double priceAtPurchase;
        private int    quantity;
        private String imageUrl;

        public OrderItemResponseDto(
                Long   id,
                Long   productId,
                String productName,
                double priceAtPurchase,
                int    quantity,
                String imageUrl) {

            this.id              = id;
            this.productId       = productId;
            this.productName     = productName;
            this.priceAtPurchase = priceAtPurchase;
            this.quantity        = quantity;
            this.imageUrl        = imageUrl;
        }

        public Long   getId()                { return id; }
        public Long   getProductId()         { return productId; }
        public String getProductName()       { return productName; }
        public double getPriceAtPurchase()   { return priceAtPurchase; }
        public int    getQuantity()          { return quantity; }
        public String getImageUrl()          { return imageUrl; }
    }
}
