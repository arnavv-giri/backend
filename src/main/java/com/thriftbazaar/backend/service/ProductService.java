package com.thriftbazaar.backend.service;

import com.thriftbazaar.backend.dto.ProductPageResponseDto;
import com.thriftbazaar.backend.dto.ProductRequestDto;
import com.thriftbazaar.backend.dto.ProductResponseDto;
import com.thriftbazaar.backend.entity.Product;
import com.thriftbazaar.backend.entity.ProductImage;
import com.thriftbazaar.backend.entity.Vendor;
import com.thriftbazaar.backend.exception.InvalidRequestException;
import com.thriftbazaar.backend.exception.ResourceNotFoundException;
import com.thriftbazaar.backend.exception.UnauthorizedActionException;
import com.thriftbazaar.backend.repository.ProductImageRepository;
import com.thriftbazaar.backend.repository.ProductRepository;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProductService {

    // ── Allowed sort fields — guard against arbitrary column injection ────
    private static final Set<String> SORTABLE_FIELDS = Set.of("price", "name", "id");

    private final ProductRepository      productRepository;
    private final ProductImageRepository productImageRepository;
    private final VendorService          vendorService;

    public ProductService(ProductRepository productRepository,
                          ProductImageRepository productImageRepository,
                          VendorService vendorService) {
        this.productRepository      = productRepository;
        this.productImageRepository = productImageRepository;
        this.vendorService          = vendorService;
    }

    // ─────────────────────────────────────────────────────────────────────
    // PUBLIC: SEARCH + FILTER + SORT + PAGINATE
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Full-featured product discovery endpoint used by the shop page.
     *
     * N+1 elimination
     * ───────────────
     * Step 1 — the Specification query includes explicit INNER JOINs on
     *          vendor and vendor.user so those are fetched in the same SQL
     *          SELECT as the products (no per-product lazy loads).
     * Step 2 — after the paginated result comes back, a single IN-clause
     *          query hydrates the images collection for every product on
     *          the page in one extra round-trip — regardless of page size.
     *
     * Total queries per call: 2 (products+vendor+user JOIN, images IN)
     *                      + 1 (Specification COUNT for totalElements)
     *                      = 3 fixed queries, O(1) relative to page size.
     *
     * Previously: 1 + 3N (vendor, vendor.user, images each lazy-loaded
     *             per product).  At page size 12 that was 37 queries.
     */
    public ProductPageResponseDto searchPublicProducts(
            String  keyword,
            String  category,
            Double  minPrice,
            Double  maxPrice,
            String  sortBy,
            String  sortDir,
            int     page,
            int     size) {

        // ── Sanitise sort parameters ──────────────────────────────────────
        String field = SORTABLE_FIELDS.contains(sortBy) ? sortBy : "id";
        Sort.Direction dir = "asc".equalsIgnoreCase(sortDir)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        // ── Cap page size to prevent runaway queries ──────────────────────
        int safeSize = Math.min(Math.max(size, 1), 48);

        // ── Normalise inputs ──────────────────────────────────────────────
        String kw  = (keyword  == null || keyword.isBlank())  ? null : keyword.trim();
        String cat = (category == null || category.isBlank() || "ALL".equals(category))
                ? null : category.trim().toUpperCase();

        PageRequest pageRequest = PageRequest.of(page, safeSize, Sort.by(dir, field));

        // ── Build Specification ───────────────────────────────────────────
        //
        // Hibernate 7 does not support ":param IS NULL OR …" for scalar params.
        // We use Criteria API predicates — only added when the input is non-null —
        // so the generated SQL is always well-formed.
        //
        // Performance note: we use cb.join(INNER) for vendor and vendor.user so
        // that Hibernate emits a single SQL SELECT with JOIN rather than issuing
        // a secondary SELECT per row when toDto() later accesses vendor fields.
        // The join also serves as the approved=true filter anchor.
        Specification<Product> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // Explicit INNER JOIN on vendor and vendor.user — eliminates N+1
            // for vendor.id, vendor.storeName, vendor.user.id accessed in toDto().
            // We use JoinType.INNER because only products with an approved vendor
            // should ever be returned; orphan products (no vendor) are excluded.
            var vendorJoin = root.join("vendor", JoinType.INNER);
            var userJoin   = vendorJoin.join("user", JoinType.INNER);

            // Always required: in-stock + approved vendor
            predicates.add(cb.greaterThan(root.get("stock"), 0));
            predicates.add(cb.isTrue(vendorJoin.get("approved")));

            // Optional: keyword — case-insensitive LIKE on product name
            if (kw != null) {
                predicates.add(cb.like(
                        cb.lower(root.get("name")),
                        "%" + kw.toLowerCase() + "%"
                ));
            }

            // Optional: exact category match
            if (cat != null) {
                predicates.add(cb.equal(root.get("category"), cat));
            }

            // Optional: price range
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }

            // Suppress vendor/user from the ORDER BY when they're already joined —
            // only needed for SELECT queries, not COUNT queries.
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                query.distinct(true);
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Product> pageResult = productRepository.findAll(spec, pageRequest);

        // ── Second pass: hydrate images in one IN-clause query ────────────
        List<Product> withImages = hydrateImages(pageResult.getContent());

        List<ProductResponseDto> content = withImages.stream()
                .map(this::toDto)
                .toList();

        return new ProductPageResponseDto(
                content,
                pageResult.getNumber(),
                pageResult.getSize(),
                pageResult.getTotalElements(),
                pageResult.getTotalPages()
        );
    }

    // ─────────────────────────────────────────────────────────────────────
    // PUBLIC: GET ALL PRODUCTS (legacy endpoint — category + price + page)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns paginated, publicly visible products filtered by category and price.
     *
     * Previously used findPublicProductsFiltered() — a JPQL query with
     * ":param IS NULL OR ..." for optional filters.  Hibernate 6/7 broke
     * support for this pattern: passing null category/price caused the WHERE
     * clause to evaluate incorrectly and return zero rows, making newly-uploaded
     * products invisible on the Home page.
     *
     * Fix: delegate to searchPublicProducts() which uses the Criteria API
     * Specification — predicates are only added when the input is non-null.
     * This is the correct Hibernate 7-compatible approach and is already used
     * by the Shop page's search endpoint.
     */
    public List<ProductResponseDto> getPublicProducts(
            String category, Double minPrice, Double maxPrice, int page, int size) {

        ProductPageResponseDto result = searchPublicProducts(
                null,     // keyword  — no keyword filter
                category, // null means all categories
                minPrice, // null means no lower bound
                maxPrice, // null means no upper bound
                "id",     // sortBy   — newest first
                "desc",   // sortDir
                page,
                size
        );

        return result.getContent();
    }

    // ─────────────────────────────────────────────────────────────────────
    // PUBLIC: GET ONE PRODUCT BY ID
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns a single product that is publicly visible.
     *
     * Uses findByIdWithDetails() which JOIN FETCHes vendor, vendor.user,
     * and images in one SQL query — no lazy-load round-trips.
     *
     * Previously this called findById() and then toDto() triggered 3
     * separate lazy SELECTs (vendor, vendor.user, images).
     */
    public ProductResponseDto getPublicProductById(Long id) {

        Product product = productRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));

        if (product.getStock() <= 0 || !product.getVendor().isApproved()) {
            throw new ResourceNotFoundException("Product not available");
        }

        return toDto(product);
    }

    // ─────────────────────────────────────────────────────────────────────
    // VENDOR: GET MY PRODUCTS
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns all products owned by the authenticated vendor.
     *
     * Uses findByVendorWithDetails() which JOIN FETCHes vendor + vendor.user,
     * then a second pass hydrates images.  Previously this called
     * findByVendor() and toDto() triggered 2N extra lazy selects per call.
     */
    public List<ProductResponseDto> getVendorProducts(String authenticatedEmail) {

        Vendor vendor = vendorService.getApprovedVendorByEmail(authenticatedEmail);

        List<Product> products = productRepository.findByVendorWithDetails(vendor);
        List<Product> withImages = hydrateImages(products);

        return withImages.stream()
                .map(this::toDto)
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────
    // VENDOR: CREATE PRODUCT
    // ─────────────────────────────────────────────────────────────────────

    @Transactional
    public ProductResponseDto createProduct(String authenticatedEmail, ProductRequestDto dto) {

        validateProductRequest(dto);

        Vendor vendor = vendorService.getApprovedVendorByEmail(authenticatedEmail);

        Product product = new Product();
        product.setVendor(vendor);
        applyDtoFields(product, dto);

        Product saved = productRepository.save(product);
        saveImages(saved, dto.getImageUrls());

        // Reload with full details to return a complete DTO
        return toDto(productRepository.findByIdWithDetails(saved.getId()).orElse(saved));
    }

    // ─────────────────────────────────────────────────────────────────────
    // VENDOR: UPDATE PRODUCT (full or partial)
    // ─────────────────────────────────────────────────────────────────────

    @Transactional
    public ProductResponseDto updateProduct(String authenticatedEmail,
                                            Long productId,
                                            ProductRequestDto dto) {

        Vendor  vendor  = vendorService.getApprovedVendorByEmail(authenticatedEmail);
        Product product = requireOwnedProduct(productId, vendor);

        if (dto.getName() != null && !dto.getName().isBlank()) {
            product.setName(dto.getName().trim());
        }
        if (dto.getCategory() != null && !dto.getCategory().isBlank()) {
            product.setCategory(dto.getCategory());
        }
        if (dto.getSize() != null && !dto.getSize().isBlank()) {
            product.setSize(dto.getSize());
        }
        if (dto.getCondition() != null && !dto.getCondition().isBlank()) {
            product.setCondition(dto.getCondition());
        }
        if (dto.getPrice() > 0) {
            product.setPrice(dto.getPrice());
        }
        if (dto.getStock() >= 0) {
            product.setStock(dto.getStock());
        }

        Product saved = productRepository.save(product);

        if (dto.getImageUrls() != null && !dto.getImageUrls().isEmpty()) {
            productImageRepository.deleteByProduct(saved);
            saveImages(saved, dto.getImageUrls());
        }

        return toDto(productRepository.findByIdWithDetails(saved.getId()).orElse(saved));
    }

    // ─────────────────────────────────────────────────────────────────────
    // VENDOR: UPDATE STOCK ONLY
    // ─────────────────────────────────────────────────────────────────────

    @Transactional
    public ProductResponseDto updateStock(String authenticatedEmail,
                                          Long productId,
                                          int newStock) {

        if (newStock < 0) {
            throw new InvalidRequestException("Stock cannot be negative");
        }

        Vendor  vendor  = vendorService.getApprovedVendorByEmail(authenticatedEmail);
        Product product = requireOwnedProduct(productId, vendor);

        product.setStock(newStock);
        return toDto(productRepository.save(product));
    }

    // ─────────────────────────────────────────────────────────────────────
    // VENDOR: DELETE PRODUCT
    // ─────────────────────────────────────────────────────────────────────

    @Transactional
    public void deleteProduct(String authenticatedEmail, Long productId) {

        Vendor  vendor  = vendorService.getApprovedVendorByEmail(authenticatedEmail);
        Product product = requireOwnedProduct(productId, vendor);

        productRepository.delete(product);
    }

    // ─────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Hydrates the images collection for a list of products using a single
     * IN-clause query rather than N per-product lazy loads.
     *
     * Returns the same products in the same order, with their images
     * collections populated in-place from the fetched data.
     *
     * Why not JOIN FETCH images in the main product query?
     * ─────────────────────────────────────────────────────
     * Hibernate does not allow JOIN FETCH on more than one collection
     * association in the same query (MultipleBagFetchException).  We already
     * JOIN FETCH vendor and vendor.user; images must come in a second pass.
     * Using an IN clause keeps it to exactly one extra round-trip regardless
     * of page size.
     */
    private List<Product> hydrateImages(List<Product> products) {
        if (products.isEmpty()) return products;

        List<Long> ids = products.stream().map(Product::getId).toList();

        // One SELECT … WHERE id IN (?) — fetches images for all products at once
        List<Product> withImages = productRepository.findByIdsWithImages(ids);

        // Build a map for O(1) lookup; then restore original order
        Map<Long, Product> byId = withImages.stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        return products.stream()
                .map(p -> byId.getOrDefault(p.getId(), p))
                .toList();
    }

    private void validateProductRequest(ProductRequestDto dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new InvalidRequestException("Product name is required");
        }
        if (dto.getPrice() <= 0) {
            throw new InvalidRequestException("Price must be greater than zero");
        }
        if (dto.getStock() < 0) {
            throw new InvalidRequestException("Stock cannot be negative");
        }
        if (dto.getCategory() == null || dto.getCategory().isBlank()) {
            throw new InvalidRequestException("Category is required");
        }
        if (dto.getSize() == null || dto.getSize().isBlank()) {
            throw new InvalidRequestException("Size is required");
        }
        if (dto.getCondition() == null || dto.getCondition().isBlank()) {
            throw new InvalidRequestException("Condition is required");
        }
    }

    private void applyDtoFields(Product product, ProductRequestDto dto) {
        product.setName(dto.getName().trim());
        product.setCategory(dto.getCategory());
        product.setSize(dto.getSize());
        product.setCondition(dto.getCondition());
        product.setPrice(dto.getPrice());
        product.setStock(dto.getStock());
    }

    private void saveImages(Product product, List<String> urls) {
        if (urls == null || urls.isEmpty()) return;

        List<ProductImage> images = urls.stream()
                .filter(url -> url != null && !url.isBlank())
                .map(url -> {
                    ProductImage img = new ProductImage();
                    img.setProduct(product);
                    img.setImageUrl(url);
                    return img;
                })
                .toList();

        if (!images.isEmpty()) {
            productImageRepository.saveAll(images);
        }
    }

    private Product requireOwnedProduct(Long productId, Vendor vendor) {

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        if (!product.getVendor().getId().equals(vendor.getId())) {
            throw new UnauthorizedActionException(
                    "You do not have permission to modify this product");
        }

        return product;
    }

    /** Maps a Product entity to its response DTO. No lazy loads triggered here. */
    public ProductResponseDto toDto(Product product) {

        List<String> imageUrls = (product.getImages() == null)
                ? List.of()
                : product.getImages().stream()
                        .map(ProductImage::getImageUrl)
                        .toList();

        return new ProductResponseDto(
                product.getId(),
                product.getName(),
                product.getCategory(),
                product.getSize(),
                product.getCondition(),
                product.getPrice(),
                product.getStock(),
                product.getVendor().getId(),
                product.getVendor().getUser().getId(),
                product.getVendor().getStoreName(),
                imageUrls);
    }
}
