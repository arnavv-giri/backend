package com.thriftbazaar.backend.dto;

import java.time.LocalDateTime;

/**
 * Returned by the review endpoints.
 *
 * reviewerEmail is included so the UI can show who wrote each review
 * and highlight "Your review" for the current user.
 */
public class ReviewResponseDto {

    private Long          id;
    private Long          productId;
    private String        reviewerEmail;
    private int           rating;
    private String        comment;
    private LocalDateTime createdAt;

    public ReviewResponseDto(
            Long          id,
            Long          productId,
            String        reviewerEmail,
            int           rating,
            String        comment,
            LocalDateTime createdAt) {
        this.id            = id;
        this.productId     = productId;
        this.reviewerEmail = reviewerEmail;
        this.rating        = rating;
        this.comment       = comment;
        this.createdAt     = createdAt;
    }

    public Long          getId()            { return id; }
    public Long          getProductId()     { return productId; }
    public String        getReviewerEmail() { return reviewerEmail; }
    public int           getRating()        { return rating; }
    public String        getComment()       { return comment; }
    public LocalDateTime getCreatedAt()     { return createdAt; }
}
