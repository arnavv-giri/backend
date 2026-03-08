package com.thriftbazaar.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a single customer order.
 *
 * Lifecycle:
 *   PENDING → PROCESSING → SHIPPED → DELIVERED
 *   Any state (except DELIVERED/CANCELLED) → CANCELLED
 *
 * Payment lifecycle (tracked separately from fulfilment status):
 *   UNPAID  — order created, payment not yet initiated or failed
 *   PAID    — Razorpay payment verified successfully on the backend
 *
 * CascadeType.ALL + orphanRemoval on items ensures that every
 * OrderItem is created/deleted together with its parent Order.
 * The entire operation is wrapped in a single @Transactional
 * boundary in OrderService.checkout() so a partial failure
 * rolls back both the Order row and any OrderItem rows.
 */
@Entity
@Table(
    name = "orders",
    indexes = {
        // Composite index: satisfies WHERE customer_id = ? ORDER BY created_at DESC
        @Index(name = "idx_order_customer_created", columnList = "customer_id, created_at")
    }
)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The customer who placed this order. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    /**
     * Fulfilment status.
     * Allowed values enforced by OrderService:
     * PENDING | PROCESSING | SHIPPED | DELIVERED | CANCELLED
     */
    @Column(nullable = false)
    private String status = "PENDING";

    /**
     * Payment status — set by PaymentService after Razorpay verification.
     * Values: UNPAID | PAID
     * Defaults to UNPAID on order creation.
     */
    @Column(nullable = false)
    private String paymentStatus = "UNPAID";

    /**
     * The Razorpay order ID returned when we create a payment order
     * via the Razorpay API (e.g. "order_XXXXXXXXXXXXXXXX").
     * Null until the customer initiates payment.
     */
    @Column(unique = true)
    private String razorpayOrderId;

    /**
     * The Razorpay payment ID returned after successful payment
     * (e.g. "pay_XXXXXXXXXXXXXXXX").
     * Null until the payment is verified.
     */
    @Column
    private String razorpayPaymentId;

    /** Sum of (priceAtPurchase × quantity) for all line items. */
    @Column(nullable = false)
    private double totalAmount;

    /** Timestamp set server-side in OrderService — never trusted from the client. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Full address string captured at checkout time. */
    @Column(nullable = false, length = 500)
    private String deliveryAddress;

    @OneToMany(
        mappedBy      = "order",
        cascade       = CascadeType.ALL,
        orphanRemoval = true,
        fetch         = FetchType.LAZY
    )
    private List<OrderItem> items = new ArrayList<>();

    // ── Getters & Setters ─────────────────────────────────────────────────

    public Long getId()                             { return id; }

    public User getCustomer()                       { return customer; }
    public void setCustomer(User customer)          { this.customer = customer; }

    public String getStatus()                       { return status; }
    public void setStatus(String status)            { this.status = status; }

    public String getPaymentStatus()                        { return paymentStatus; }
    public void   setPaymentStatus(String paymentStatus)    { this.paymentStatus = paymentStatus; }

    public String getRazorpayOrderId()                          { return razorpayOrderId; }
    public void   setRazorpayOrderId(String razorpayOrderId)    { this.razorpayOrderId = razorpayOrderId; }

    public String getRazorpayPaymentId()                            { return razorpayPaymentId; }
    public void   setRazorpayPaymentId(String razorpayPaymentId)    { this.razorpayPaymentId = razorpayPaymentId; }

    public double getTotalAmount()                  { return totalAmount; }
    public void setTotalAmount(double totalAmount)  { this.totalAmount = totalAmount; }

    public LocalDateTime getCreatedAt()                      { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt)        { this.createdAt = createdAt; }

    public String getDeliveryAddress()                       { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress)   { this.deliveryAddress = deliveryAddress; }

    public List<OrderItem> getItems()                        { return items; }
    public void setItems(List<OrderItem> items)              { this.items = items; }

    /**
     * Convenience helper: links the item to this order (both sides of the
     * bi-directional association) and appends it to the collection.
     */
    public void addItem(OrderItem item) {
        item.setOrder(this);
        this.items.add(item);
    }

    // equals/hashCode based on database ID so that LinkedHashSet deduplication
    // in OrderService works correctly when JOIN FETCH produces duplicate roots.
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Order other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
