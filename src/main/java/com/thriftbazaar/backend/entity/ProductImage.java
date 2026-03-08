package com.thriftbazaar.backend.entity;

import jakarta.persistence.*;

/**
 * An image URL associated with a product.
 *
 * The @ManyToOne back-reference to Product is LAZY so that loading a
 * ProductImage record (e.g. via deleteByProduct) does not re-fetch the
 * entire product graph.
 */
@Entity
@Table(
    name = "product_images",
    indexes = {
        @Index(name = "idx_product_image_product_id", columnList = "product_id")
    }
)
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // LAZY — prevents product re-fetch when images are loaded independently
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false)
    private String imageUrl;

    // getters & setters
    public Long getId() { return id; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
}
