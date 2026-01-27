package com.thriftbazaar.backend.dto;

import java.util.List;

public class CartResponseDto {

    private List<CartItemResponseDto> items;
    private double totalAmount;

    public CartResponseDto(List<CartItemResponseDto> items, double totalAmount) {
        this.items = items;
        this.totalAmount = totalAmount;
    }

    public List<CartItemResponseDto> getItems() {
        return items;
    }

    public double getTotalAmount() {
        return totalAmount;
    }
}
