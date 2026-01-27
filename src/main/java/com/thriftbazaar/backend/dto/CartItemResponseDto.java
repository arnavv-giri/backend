package com.thriftbazaar.backend.dto;

public class CartItemResponseDto {

    private Long productId;
    private String name;
    private double price;
    private int quantity;

    public CartItemResponseDto(
            Long productId,
            String name,
            double price,
            int quantity
    ) {
        this.productId = productId;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }

    // getters
}
