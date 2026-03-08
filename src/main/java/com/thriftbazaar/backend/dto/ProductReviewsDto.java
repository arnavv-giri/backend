package com.thriftbazaar.backend.dto;

import java.util.List;

/**
 * Returned by GET /reviews/product/{productId}?page=0&size=10
 *
 * Extends the original shape with pagination metadata so the frontend
 * can render a "Load more" control without fetching all reviews upfront.
 *
 * Fields
 * ──────
 * reviews       – reviews on the current page, newest first
 * averageRating – mean of ALL ratings across all pages (not just this page)
 * reviewCount   – total number of reviews across all pages
 * page          – zero-based current page index
 * totalPages    – total number of pages at the requested size
 * hasMore       – convenience flag: true when more pages exist
 *
 * Backwards-compatibility note
 * ────────────────────────────
 * The original DTO had {reviews, averageRating, reviewCount}.
 * The new fields (page, totalPages, hasMore) are additions only —
 * existing consumers that ignore unknown fields are unaffected.
 */
public class ProductReviewsDto {

    private List<ReviewResponseDto> reviews;
    private Double                  averageRating;
    private long                    reviewCount;
    private int                     page;
    private int                     totalPages;
    private boolean                 hasMore;

    public ProductReviewsDto(
            List<ReviewResponseDto> reviews,
            Double  averageRating,
            long    reviewCount,
            int     page,
            int     totalPages) {

        this.reviews       = reviews;
        this.averageRating = averageRating;
        this.reviewCount   = reviewCount;
        this.page          = page;
        this.totalPages    = totalPages;
        this.hasMore       = page < (totalPages - 1);
    }

    public List<ReviewResponseDto> getReviews()       { return reviews; }
    public Double                  getAverageRating() { return averageRating; }
    public long                    getReviewCount()   { return reviewCount; }
    public int                     getPage()          { return page; }
    public int                     getTotalPages()    { return totalPages; }
    public boolean                 isHasMore()        { return hasMore; }
}
