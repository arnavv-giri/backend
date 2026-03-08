package com.thriftbazaar.backend.entity;

import jakarta.persistence.*;

/**
 * A single line item inside an Order.
 *
 * We deliberately snapshot productName and priceAtPurchase at checkout
 * time rather than linking back to the live Product.  This means:
 *   - The order record stays accurate even if the product is later edited or deleted.
 *   - We still keep a nullable productId FK for "contact seller" lookups.
 *
 * The product FK is nullable (SET NULL) so that deleting a product does not
 * cascade and destroy historical order data.
 */
@Entity
@Table(
    name = "order_items",
    indexes = {
        @Index(name = "idx_order_item_order_id",   columnList = "order_id"),
        @Index(name = "idx_order_item_product_id",  columnList = "product_id")
    }
)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /**
     * Nullable: if the product is deleted after purchase the order row
     * remains intact but this FK becomes null.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = true)
    private Product product;

    /** Snapshot of the product name at purchase time. */
    @Column(nullable = false)
    private String productName;

    /** Snapshot of the unit price at purchase time. */
    @Column(nullable = false)
    private double priceAtPurchase;

    @Column(nullable = false)
    private int quantity;

    /** First image URL snapshotted at purchase so the order page can show a thumbnail. */
    @Column(length = 1000)
    private String imageUrl;

    // ── Getters & Setters ─────────────────────────────────────────────────

    public Long getId()                              { return id; }

    public Order getOrder()                          { return order; }
    public void  setOrder(Order order)               { this.order = order; }

    public Product getProduct()                      { return product; }
    public void    setProduct(Product product)       { this.product = product; }

    public String getProductName()                   { return productName; }
    public void   setProductName(String productName) { this.productName = productName; }

    public double getPriceAtPurchase()                          { return priceAtPurchase; }
    public void   setPriceAtPurchase(double priceAtPurchase)    { this.priceAtPurchase = priceAtPurchase; }

    public int  getQuantity()               { return quantity; }
    public void setQuantity(int quantity)   { this.quantity = quantity; }

    public String getImageUrl()                  { return imageUrl; }
    public void   setImageUrl(String imageUrl)   { this.imageUrl = imageUrl; }
}
