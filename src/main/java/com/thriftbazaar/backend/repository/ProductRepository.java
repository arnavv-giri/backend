package com.thriftbazaar.backend.repository;

import com.thriftbazaar.backend.entity.Product;
import com.thriftbazaar.backend.entity.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Product repository.
 *
 * N+1 elimination strategy
 * ────────────────────────
 * ProductService.toDto() accesses product.vendor.id, product.vendor.user.id,
 * product.vendor.storeName, and product.images on every product it maps.
 *
 * With LAZY fetch (which is correct for the entity declaration) this causes:
 *   - 1 SELECT for the product list
 *   - N SELECTs for vendor         (one per product)
 *   - N SELECTs for vendor.user    (one per product)
 *   - N SELECTs for images         (one per product)
 * = 1 + 3N queries per page load.  At page size 12: 37 queries.
 *
 * The JOIN FETCH queries below collapse those into 2 queries per page:
 *   Query 1 — paginated products JOIN FETCH vendor JOIN FETCH vendor.user
 *   Query 2 — products JOIN FETCH images  (separate pass; Hibernate cannot
 *             JOIN FETCH two collection associations in one query)
 *
 * The separate images fetch uses an IN clause rather than a loop, so it
 * is always a single round-trip regardless of how many products are on the page.
 *
 * Specification executor is kept for the dynamic search endpoint.
 */
public interface ProductRepository
        extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    // ── Vendor helpers ────────────────────────────────────────────────────

    /**
     * Returns all products for a vendor with vendor + user + images eagerly loaded.
     *
     * Uses two queries (vendor JOIN FETCH + images IN fetch) instead of
     * the bare findByVendor() which triggered 2N lazy-load selects when
     * the seller dashboard mapped each product to a DTO.
     */
    @Query("""
        SELECT p FROM Product p
        JOIN FETCH p.vendor v
        JOIN FETCH v.user
        WHERE p.vendor = :vendor
        ORDER BY p.id DESC
    """)
    List<Product> findByVendorWithDetails(@Param("vendor") Vendor vendor);

    /**
     * Bulk-load images for a set of product IDs in one query.
     * Called after any paginated or list query to hydrate the images
     * collection without an N+1 per-product fetch.
     *
     * Hibernate initialises the in-memory collections from this result
     * when the products are still attached to the session.
     */
    @Query("""
        SELECT p FROM Product p
        LEFT JOIN FETCH p.images
        WHERE p.id IN :ids
    """)
    List<Product> findByIdsWithImages(@Param("ids") List<Long> ids);

    // ── Single product fetch ──────────────────────────────────────────────

    /**
     * Fetches one product with vendor + vendor.user + images all in one query.
     * Used by getPublicProductById() to avoid 3 lazy-load round-trips.
     */
    @Query("""
        SELECT p FROM Product p
        JOIN FETCH p.vendor v
        JOIN FETCH v.user
        LEFT JOIN FETCH p.images
        WHERE p.id = :id
    """)
    Optional<Product> findByIdWithDetails(@Param("id") Long id);

    // NOTE: findPublicProductsFiltered() has been removed.
    //       Hibernate 6/7 broke support for ":param IS NULL" checks on bind
    //       parameters in JPQL — passing null category/price caused the query
    //       to silently return zero results on the Home page.
    //
    //       The legacy getPublicProducts() service method now delegates to the
    //       same JpaSpecificationExecutor path used by searchPublicProducts(),
    //       which builds predicates only for non-null inputs and is fully
    //       Hibernate-7 compatible.
    //
    // NOTE: searchPublicProducts(@Query) remains removed.
    //       The service calls findAll(Specification, Pageable) from
    //       JpaSpecificationExecutor, which is Hibernate-7 compatible.
    //       After the page is retrieved, ProductService calls
    //       findByIdsWithImages() in a second pass to hydrate images.
}
