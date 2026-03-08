package com.thriftbazaar.backend.service;

import com.thriftbazaar.backend.dto.ProductReviewsDto;
import com.thriftbazaar.backend.dto.ReviewRequestDto;
import com.thriftbazaar.backend.dto.ReviewResponseDto;
import com.thriftbazaar.backend.dto.ReviewStatsDto;
import com.thriftbazaar.backend.entity.Product;
import com.thriftbazaar.backend.entity.Review;
import com.thriftbazaar.backend.entity.User;
import com.thriftbazaar.backend.exception.DuplicateResourceException;
import com.thriftbazaar.backend.exception.InvalidRequestException;
import com.thriftbazaar.backend.exception.ResourceNotFoundException;
import com.thriftbazaar.backend.exception.UnauthorizedActionException;
import com.thriftbazaar.backend.repository.OrderRepository;
import com.thriftbazaar.backend.repository.ProductRepository;
import com.thriftbazaar.backend.repository.ReviewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    /** Maximum reviews per page — prevents callers requesting 10,000 at once. */
    private static final int MAX_PAGE_SIZE = 50;

    private final ReviewRepository  reviewRepository;
    private final ProductRepository productRepository;
    private final OrderRepository   orderRepository;
    private final UserService       userService;

    public ReviewService(ReviewRepository  reviewRepository,
                         ProductRepository productRepository,
                         OrderRepository   orderRepository,
                         UserService       userService) {
        this.reviewRepository  = reviewRepository;
        this.productRepository = productRepository;
        this.orderRepository   = orderRepository;
        this.userService       = userService;
    }

    // ─────────────────────────────────────────────────────────────────────
    // SUBMIT REVIEW
    // ─────────────────────────────────────────────────────────────────────

    @Transactional
    public ReviewResponseDto submitReview(String authenticatedEmail,
                                          Long productId,
                                          ReviewRequestDto dto) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        if (dto.getRating() < 1 || dto.getRating() > 5) {
            throw new InvalidRequestException("Rating must be between 1 and 5");
        }

        User reviewer = userService.getByEmail(authenticatedEmail);

        if (!orderRepository.existsByCustomerAndDeliveredItemForProduct(reviewer, product)) {
            throw new UnauthorizedActionException(
                    "You can only review products from your delivered orders");
        }

        if (reviewRepository.existsByProductAndReviewer(product, reviewer)) {
            throw new DuplicateResourceException("You have already reviewed this product");
        }

        Review review = new Review();
        review.setProduct(product);
        review.setReviewer(reviewer);
        review.setRating(dto.getRating());
        review.setComment(
                (dto.getComment() != null && !dto.getComment().isBlank())
                        ? dto.getComment().trim()
                        : null
        );
        review.setCreatedAt(LocalDateTime.now());

        Review saved = reviewRepository.save(review);
        log.info("Review submitted — reviewId={} productId={} reviewerId={} rating={}",
                saved.getId(), productId, reviewer.getId(), dto.getRating());

        return toDto(saved);
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET REVIEWS FOR A PRODUCT (paginated)
    // ─────────────────────────────────────────────────────────────────────

    public ProductReviewsDto getReviewsForProduct(Long productId, int page, int size) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Page<Review> reviewPage = reviewRepository.findByProductWithReviewer(
                product, PageRequest.of(page, safeSize));

        List<ReviewResponseDto> reviewDtos = reviewPage.getContent()
                .stream()
                .map(this::toDto)
                .toList();

        ReviewStatsDto stats  = reviewRepository.findStatsByProduct(product);
        Double avg            = stats.getAverageRating();
        long   count          = stats.getReviewCount();
        Double rounded        = (avg != null) ? Math.round(avg * 10.0) / 10.0 : null;

        return new ProductReviewsDto(
                reviewDtos, rounded, count,
                reviewPage.getNumber(), reviewPage.getTotalPages());
    }

    // ─────────────────────────────────────────────────────────────────────
    // CAN REVIEW?
    // ─────────────────────────────────────────────────────────────────────

    public boolean canReview(String authenticatedEmail, Long productId) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        User reviewer = userService.getByEmail(authenticatedEmail);

        return orderRepository.existsByCustomerAndDeliveredItemForProduct(reviewer, product)
                && !reviewRepository.existsByProductAndReviewer(product, reviewer);
    }

    // ─────────────────────────────────────────────────────────────────────
    // PRIVATE
    // ─────────────────────────────────────────────────────────────────────

    private ReviewResponseDto toDto(Review review) {
        return new ReviewResponseDto(
                review.getId(),
                review.getProduct().getId(),
                review.getReviewer().getEmail(),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt()
        );
    }
}
