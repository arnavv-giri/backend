package com.thriftbazaar.backend.entity;
import java.util.List;
import jakarta.persistence.*;

/**
 * A product listed by a vendor.
 *
 * Performance notes
 * ─────────────────
 * vendor      – LAZY fetch.  The default (EAGER) would join vendors + users on
 *               every single product load.  ProductService.toDto() accesses
 *               vendor fields, but that happens inside an open Hibernate session
 *               (either within a @Transactional boundary or Spring's open-session-
 *               in-view, which we disable).  LAZY is always correct here.
 *
 * images      – LAZY (already the JPA default for @OneToMany).
 *
 * Composite index on (vendor_id, stock) — the two most frequent WHERE predicates
 * in every public product query:
 *   WHERE p.stock > 0 AND p.vendor.approved = true
 * Adding vendor_id first keeps the index selective even with many vendors.
 */
@Entity
@Table(
    name = "products",
    indexes = {
        @Index(name = "idx_product_vendor_stock", columnList = "vendor_id, stock"),
        @Index(name = "idx_product_category",     columnList = "category"),
        @Index(name = "idx_product_price",        columnList = "price")
    }
)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // LAZY — avoids an automatic JOIN to vendors (and then to users) on every
    // product fetch. The vendor is only dereferenced inside service-layer methods
    // that already hold an open session.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendor_id", nullable = false)
    private Vendor vendor;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String size;

    @Column(nullable = false)
    private String condition;

    @Column(nullable = false)
    private double price;

    @Column(nullable = false)
    private int stock;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> images;


    // getters & setters

    public Long getId() {
        return id;
    }

    public Vendor getVendor() {
        return vendor;
    }

    public void setVendor(Vendor vendor) {
        this.vendor = vendor;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getCondition() {
        return condition;
    }

    public void setCondition(String condition) {
        this.condition = condition;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public List<ProductImage> getImages() {
        return images;
    }
}
