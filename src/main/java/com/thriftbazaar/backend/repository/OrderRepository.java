package com.thriftbazaar.backend.repository;

import com.thriftbazaar.backend.entity.Order;
import com.thriftbazaar.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // Get all orders of a customer (for order history)
    List<Order> findByUser(User user);
}
