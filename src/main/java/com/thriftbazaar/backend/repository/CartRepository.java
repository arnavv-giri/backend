package com.thriftbazaar.backend.repository;

import com.thriftbazaar.backend.entity.Cart;
import com.thriftbazaar.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByUser(User user);
}
