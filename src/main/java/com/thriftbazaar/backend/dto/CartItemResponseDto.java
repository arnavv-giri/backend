package com.thriftbazaar.backend.dto;

public class CartItemResponseDto {
    private Long itemId; 
    private Long productId;
    private String name;
    private double price;
    private int quantity;

    public CartItemResponseDto(
        Long itemId,
            Long productId,
            String name,
            double price,
            int quantity
    ) {
        this.itemId = itemId;
        this.productId = productId;
        this.name = name;
        this.price = price;
        this.quantity = quantity;
    }
    public Long getItemId() { return itemId; }

    public Long getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
    }

    public int getQuantity() {
        return quantity;
    }
}
