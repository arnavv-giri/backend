package com.thriftbazaar.backend.controller;

import com.thriftbazaar.backend.dto.ProductReviewsDto;
import com.thriftbazaar.backend.dto.ReviewRequestDto;
import com.thriftbazaar.backend.dto.ReviewResponseDto;
import com.thriftbazaar.backend.service.ReviewService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Handles product review endpoints.
 *
 * Endpoint summary
 * ────────────────────────────────────────────────────────────────────────
 * POST GET  /reviews/product/{productId}
 *      GET  /reviews/product/{productId}?page=0&size=10
 *      GET  /reviews/product/{productId}/can-review
 */
@RestController
@RequestMapping("/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    // ── POST /reviews/product/{productId} ────────────────────────────────
    @PostMapping("/product/{productId}")
    public ResponseEntity<ReviewResponseDto> submitReview(
            @PathVariable Long productId,
            @RequestBody  ReviewRequestDto dto,
            Authentication authentication
    ) {
        ReviewResponseDto created = reviewService.submitReview(
                authentication.getName(), productId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ── GET /reviews/product/{productId} ─────────────────────────────────
    // Public — no token required.
    // Accepts optional ?page and ?size query params (defaults: page=0, size=10).
    // Returns ProductReviewsDto { reviews[], averageRating, reviewCount,
    //                             page, totalPages, hasMore }.
    @GetMapping("/product/{productId}")
    public ResponseEntity<ProductReviewsDto> getProductReviews(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        return ResponseEntity.ok(
                reviewService.getReviewsForProduct(productId, page, size));
    }

    // ── GET /reviews/product/{productId}/can-review ───────────────────────
    @GetMapping("/product/{productId}/can-review")
    public ResponseEntity<Map<String, Boolean>> canReview(
            @PathVariable Long productId,
            Authentication authentication
    ) {
        boolean eligible = reviewService.canReview(
                authentication.getName(), productId);
        return ResponseEntity.ok(Map.of("canReview", eligible));
    }
}
