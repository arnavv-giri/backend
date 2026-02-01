package com.thriftbazaar.backend.dto;

import com.thriftbazaar.backend.entity.OrderStatus;

public class UpdateOrderStatusRequestDto {

    private OrderStatus status;

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }
}
