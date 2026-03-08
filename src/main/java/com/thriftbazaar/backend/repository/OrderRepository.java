package com.thriftbazaar.backend.repository;

import com.thriftbazaar.backend.entity.Order;
import com.thriftbazaar.backend.entity.Product;
import com.thriftbazaar.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Returns all orders belonging to a customer, newest first.
     *
     * Hibernate 6/7 (Spring Boot 3+/4+) throws a SemanticException when
     * DISTINCT is combined with ORDER BY on a collection-fetch-joined query.
     * Fix: remove DISTINCT from the JPQL and deduplicate in Java instead.
     * The LEFT JOIN FETCH still loads items eagerly in one SQL round-trip;
     * duplicate Order roots that arise from the join are collapsed by the
     * LinkedHashSet below while preserving insertion (newest-first) order.
     */
    @Query("""
        SELECT o FROM Order o
        LEFT JOIN FETCH o.items
        WHERE o.customer = :customer
        ORDER BY o.createdAt DESC
    """)
    List<Order> findByCustomerWithItems(@Param("customer") User customer);

    /**
     * Loads a single order together with its items.
     * Used by getOrderById() and updateStatus().
     */
    @Query("""
        SELECT o FROM Order o
        LEFT JOIN FETCH o.items
        WHERE o.id = :id
    """)
    Optional<Order> findByIdWithItems(@Param("id") Long id);

    /**
     * Returns true if the customer has at least one DELIVERED order
     * that contains the given product.
     *
     * Used by ReviewService to verify purchase before allowing a review.
     * We check order_items.product rather than the snapshotted productName
     * because the FK is the authoritative link (it is set to null only when
     * a product is deleted, in which case reviewing is also impossible).
     */
    @Query("""
        SELECT COUNT(o) > 0 FROM Order o
        JOIN o.items i
        WHERE o.customer  = :customer
          AND i.product   = :product
          AND o.status    = 'DELIVERED'
    """)
    boolean existsByCustomerAndDeliveredItemForProduct(
            @Param("customer") User    customer,
            @Param("product")  Product product
    );

    /**
     * Returns all orders that contain at least one item belonging to the given vendor.
     *
     * DISTINCT removed for Hibernate 6/7 compatibility — ORDER BY on a
     * collection-fetch-joined query with DISTINCT causes a SemanticException.
     * Deduplication is done in Java in OrderService.getVendorOrders().
     */
    @Query("""
        SELECT o FROM Order o
        LEFT JOIN FETCH o.items i
        LEFT JOIN i.product p
        WHERE p.vendor = :vendor
        ORDER BY o.createdAt DESC
    """)
    List<Order> findOrdersContainingVendorProducts(@Param("vendor") com.thriftbazaar.backend.entity.Vendor vendor);
}
