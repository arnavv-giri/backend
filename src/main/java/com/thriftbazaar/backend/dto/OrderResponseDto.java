package com.thriftbazaar.backend.dto;

import com.thriftbazaar.backend.entity.OrderStatus;
import java.time.LocalDateTime;
import java.util.List;

public class OrderResponseDto {

    private Long orderId;
    private LocalDateTime createdAt;
    private OrderStatus status;
    private double totalAmount;
    private List<OrderItemResponseDto> items;

    public OrderResponseDto(
            Long orderId,
            LocalDateTime createdAt,
            OrderStatus status,
            double totalAmount,
            List<OrderItemResponseDto> items
    ) {
        this.orderId = orderId;
        this.createdAt = createdAt;
        this.status = status;
        this.totalAmount = totalAmount;
        this.items = items;
    }

    public Long getOrderId() {
        return orderId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public List<OrderItemResponseDto> getItems() {
        return items;
    }
}
