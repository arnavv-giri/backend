package com.thriftbazaar.backend.repository;

import com.thriftbazaar.backend.entity.Product;
import com.thriftbazaar.backend.entity.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByVendor(Vendor vendor);
    List<Product> findByStockGreaterThan(int stock);
     // ✅ PUBLIC PRODUCTS
    @Query("""
        SELECT p FROM Product p
        WHERE p.stock > 0
          AND p.vendor.approved = true
    """)
    List<Product> findPublicProducts();
     @Query("""
        SELECT p FROM Product p
        WHERE p.stock > 0
          AND p.vendor.approved = true
          AND (:category IS NULL OR p.category = :category)
          AND (:minPrice IS NULL OR p.price >= :minPrice)
          AND (:maxPrice IS NULL OR p.price <= :maxPrice)
    """)
    Page<Product> findPublicProductsFiltered(
            String category,
            Double minPrice,
            Double maxPrice,
            Pageable pageable
    );

}
