package com.thriftbazaar.backend.repository;

import com.thriftbazaar.backend.entity.Cart;
import com.thriftbazaar.backend.entity.CartItem;
import com.thriftbazaar.backend.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Query;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByCartAndProduct(Cart cart, Product product);
    @Transactional
@Modifying
@Query("DELETE FROM CartItem ci WHERE ci.cart = :cart")
void deleteByCart(Cart cart);
    
}
