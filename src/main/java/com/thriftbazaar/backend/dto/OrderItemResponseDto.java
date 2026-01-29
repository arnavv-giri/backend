package com.thriftbazaar.backend.dto;

public class OrderItemResponseDto {

    private Long productId;
    private String productName;
    private int quantity;
    private double priceAtPurchase;

    public OrderItemResponseDto(Long productId, String productName,
                                int quantity, double priceAtPurchase) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.priceAtPurchase = priceAtPurchase;
    }

    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public double getPriceAtPurchase() { return priceAtPurchase; }
}
