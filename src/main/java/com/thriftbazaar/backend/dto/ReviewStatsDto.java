package com.thriftbazaar.backend.dto;

/**
 * Carries the aggregate review statistics for one product.
 *
 * Populated by ReviewRepository.findStatsByProduct() — a single
 * SELECT AVG + COUNT query that replaces the two separate queries
 * (findAverageRatingByProduct + countByProduct) that existed before.
 *
 * averageRating is null when the product has no reviews yet.
 */
public class ReviewStatsDto {

    private final Double averageRating;
    private final long   reviewCount;

    /** Constructor used by the JPQL constructor expression in ReviewRepository. */
    public ReviewStatsDto(Double averageRating, Long reviewCount) {
        this.averageRating = averageRating;
        this.reviewCount   = (reviewCount != null) ? reviewCount : 0L;
    }

    public Double getAverageRating() { return averageRating; }
    public long   getReviewCount()   { return reviewCount; }
}
