package com.thriftbazaar.backend.dto;

public class AddToCartRequestDto {
    private Long productId;
    private int quantity;

    public Long getProductId() { return productId; }
    public int getQuantity() { return quantity; }
}
