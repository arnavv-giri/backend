package com.thriftbazaar.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A review left by a customer on a product they have purchased.
 *
 * Business rules enforced at the service layer:
 *   - The reviewer must have at least one DELIVERED order containing this product.
 *   - A reviewer may only submit one review per product (DB unique constraint).
 *   - Rating must be 1-5 inclusive.
 */
@Entity
@Table(
    name = "reviews",
    indexes = {
        // Composite index: satisfies WHERE product_id = ? ORDER BY created_at DESC
        // in a single index scan — no separate sort step for the review list query.
        @Index(name = "idx_review_product_created", columnList = "product_id, created_at"),
        @Index(name = "idx_review_reviewer_id",     columnList = "reviewer_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name        = "uq_review_product_reviewer",
            columnNames = { "product_id", "reviewer_id" }
        )
    }
)
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    /** Star rating: 1 (worst) to 5 (best). Validated in ReviewService. */
    @Column(nullable = false)
    private int rating;

    /** Optional free-text comment, up to 2000 chars. */
    @Column(length = 2000)
    private String comment;

    /** Set server-side on creation — never from the client. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // ── Getters & Setters ─────────────────────────────────────────────────

    public Long getId()                               { return id; }

    public Product getProduct()                       { return product; }
    public void    setProduct(Product product)        { this.product = product; }

    public User getReviewer()                         { return reviewer; }
    public void setReviewer(User reviewer)            { this.reviewer = reviewer; }

    public int  getRating()                           { return rating; }
    public void setRating(int rating)                 { this.rating = rating; }

    public String getComment()                        { return comment; }
    public void   setComment(String comment)          { this.comment = comment; }

    public LocalDateTime getCreatedAt()               { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
