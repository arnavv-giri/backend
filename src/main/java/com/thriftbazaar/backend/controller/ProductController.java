package com.thriftbazaar.backend.controller;

import com.thriftbazaar.backend.dto.ProductPageResponseDto;
import com.thriftbazaar.backend.dto.ProductRequestDto;
import com.thriftbazaar.backend.dto.ProductResponseDto;
import com.thriftbazaar.backend.dto.UpdateStockRequestDto;
import com.thriftbazaar.backend.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Handles product CRUD and search operations.
 *
 * Responsibilities:
 *  - Extract authenticated email from Spring Security context.
 *  - Forward all calls to ProductService.
 *  - Return ResponseEntity with appropriate HTTP status.
 *
 * No business logic. No repository access. No ownership checks. No mapping.
 *
 * Endpoint summary
 * ────────────────────────────────────────────────────────────────────────
 * GET  /products/search   → full-featured search (keyword+filter+sort+page)
 * GET  /products          → legacy list (category+price+page) — unchanged
 * GET  /products/{id}     → single product — unchanged
 * GET  /products/my       → vendor's own products — unchanged
 * POST /products          → create product — unchanged
 * PUT  /products/{id}     → update product — unchanged
 * PUT  /products/{id}/stock → update stock — unchanged
 * DEL  /products/{id}     → delete product — unchanged
 */
@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // ── GET /products/search ──────────────────────────────────────────────
    //
    // Full search + filter + sort + paginate endpoint used by the shop page.
    //
    // Query parameters
    // ─────────────────────────────────────────────────────────────────────
    // keyword   (optional) – case-insensitive substring match on product name
    // category  (optional) – exact category filter; omit or "ALL" for all
    // minPrice  (optional) – inclusive lower price bound
    // maxPrice  (optional) – inclusive upper price bound
    // sortBy    (optional) – "price" | "name" | "id"   (default: "id")
    // sortDir   (optional) – "asc"  | "desc"           (default: "desc")
    // page      (optional) – zero-based page index     (default: 0)
    // size      (optional) – items per page            (default: 12, max 48)
    //
    // Response: ProductPageResponseDto  { content, page, size, totalItems, totalPages }
    //
    // This endpoint is PUBLIC (permitted in SecurityConfig for GET /products/*).
    @GetMapping("/search")
    public ResponseEntity<ProductPageResponseDto> searchProducts(
            @RequestParam(required = false)                 String keyword,
            @RequestParam(required = false)                 String category,
            @RequestParam(required = false)                 Double minPrice,
            @RequestParam(required = false)                 Double maxPrice,
            @RequestParam(required = false, defaultValue = "id")   String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortDir,
            @RequestParam(defaultValue = "0")               int    page,
            @RequestParam(defaultValue = "12")              int    size
    ) {
        return ResponseEntity.ok(
                productService.searchPublicProducts(
                        keyword, category, minPrice, maxPrice,
                        sortBy, sortDir, page, size)
        );
    }

    // ── GET /products — Legacy list (unchanged) ───────────────────────────
    @GetMapping
    public ResponseEntity<List<ProductResponseDto>> getPublicProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "12") int size
    ) {
        return ResponseEntity.ok(
                productService.getPublicProducts(category, minPrice, maxPrice, page, size)
        );
    }

    // ── GET /products/my — Vendor: own products ───────────────────────────
    @GetMapping("/my")
    public ResponseEntity<List<ProductResponseDto>> getMyProducts(Authentication authentication) {
        return ResponseEntity.ok(
                productService.getVendorProducts(authentication.getName())
        );
    }

    // ── GET /products/{id} — Public: single product ───────────────────────
    @GetMapping("/{id}")
    public ResponseEntity<ProductResponseDto> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getPublicProductById(id));
    }

    // ── POST /products — Vendor: create ───────────────────────────────────
    @PostMapping
    public ResponseEntity<ProductResponseDto> createProduct(
            @RequestBody ProductRequestDto dto,
            Authentication authentication
    ) {
        ProductResponseDto created = productService.createProduct(
                authentication.getName(), dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ── PUT /products/{id} — Vendor: update ───────────────────────────────
    @PutMapping("/{id}")
    public ResponseEntity<ProductResponseDto> updateProduct(
            @PathVariable Long id,
            @RequestBody ProductRequestDto dto,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                productService.updateProduct(authentication.getName(), id, dto)
        );
    }

    // ── PUT /products/{id}/stock — Vendor: update stock ───────────────────
    @PutMapping("/{id}/stock")
    public ResponseEntity<ProductResponseDto> updateStock(
            @PathVariable Long id,
            @RequestBody UpdateStockRequestDto dto,
            Authentication authentication
    ) {
        return ResponseEntity.ok(
                productService.updateStock(authentication.getName(), id, dto.getStock())
        );
    }

    // ── DELETE /products/{id} — Vendor: delete ────────────────────────────
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(
            @PathVariable Long id,
            Authentication authentication
    ) {
        productService.deleteProduct(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
