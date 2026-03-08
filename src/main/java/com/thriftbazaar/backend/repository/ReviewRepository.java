package com.thriftbazaar.backend.repository;

import com.thriftbazaar.backend.dto.ReviewStatsDto;
import com.thriftbazaar.backend.entity.Product;
import com.thriftbazaar.backend.entity.Review;
import com.thriftbazaar.backend.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    /**
     * Paginated reviews for a product, newest first.
     *
     * JOIN FETCH reviewer so toDto() reads the email in the same query —
     * no secondary SELECT per review row.
     *
     * Uses a separate countQuery so Spring Data does not try to apply
     * the JOIN FETCH inside the COUNT(*) sub-query (which Hibernate rejects).
     *
     * Why paginated?
     * A popular product can accumulate thousands of reviews.  Returning all
     * of them in one response would: (a) send a huge JSON payload, (b) hold
     * a DB result-set open for the full serialisation time, and (c) render
     * thousands of DOM nodes in the browser.  A page of 10 is sufficient for
     * the initial view; users can request more via "Load more".
     */
    @Query(
        value      = "SELECT r FROM Review r JOIN FETCH r.reviewer "
                   + "WHERE r.product = :product ORDER BY r.createdAt DESC",
        countQuery = "SELECT COUNT(r) FROM Review r WHERE r.product = :product"
    )
    Page<Review> findByProductWithReviewer(
            @Param("product") Product  product,
            Pageable                   pageable
    );

    /** Does this user already have a review for this product? */
    boolean existsByProductAndReviewer(Product product, User reviewer);

    /** Fetch the existing review (e.g. to return it in canReview response). */
    Optional<Review> findByProductAndReviewer(Product product, User reviewer);

    /**
     * Returns average rating AND count in a single aggregated query.
     *
     * Previously ReviewService called findAverageRatingByProduct() and
     * countByProduct() as two separate SELECT statements after already
     * executing findByProductWithReviewer().  That was 3 queries per
     * product-page load.  This single query reduces it to 1 stats query
     * (plus the paginated reviews query), total = 2 queries regardless of
     * review volume.
     *
     * Returns null when the product has no reviews (AVG of empty set = null).
     * The service maps null → ReviewStatsDto with avg=null, count=0.
     */
    @Query("""
        SELECT new com.thriftbazaar.backend.dto.ReviewStatsDto(
                   AVG(CAST(r.rating AS double)),
                   COUNT(r)
               )
        FROM Review r
        WHERE r.product = :product
    """)
    ReviewStatsDto findStatsByProduct(@Param("product") Product product);
}
