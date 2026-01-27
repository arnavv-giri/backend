package com.thriftbazaar.backend.repository;

import com.thriftbazaar.backend.entity.Cart;
import com.thriftbazaar.backend.entity.CartItem;
import com.thriftbazaar.backend.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByCartAndProduct(Cart cart, Product product);
    
}
