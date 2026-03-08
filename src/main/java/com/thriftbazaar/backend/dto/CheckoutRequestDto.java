package com.thriftbazaar.backend.dto;

import java.util.List;

/**
 * Payload sent by the frontend when the customer clicks "Place Order".
 *
 * items        – list of cart line items (each carries a productId and quantity)
 * deliveryAddress – full address string assembled by the frontend from the form fields
 */
public class CheckoutRequestDto {

    private List<CheckoutItemDto> items;
    private String deliveryAddress;

    public List<CheckoutItemDto> getItems()              { return items; }
    public void setItems(List<CheckoutItemDto> items)    { this.items = items; }

    public String getDeliveryAddress()                       { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress)   { this.deliveryAddress = deliveryAddress; }

    // ── Inner DTO for each cart line ──────────────────────────────────────

    public static class CheckoutItemDto {

        private Long productId;
        private int  quantity;

        public Long getProductId()                 { return productId; }
        public void setProductId(Long productId)   { this.productId = productId; }

        public int  getQuantity()               { return quantity; }
        public void setQuantity(int quantity)   { this.quantity = quantity; }
    }
}
