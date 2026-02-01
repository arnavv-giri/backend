package com.thriftbazaar.backend.controller;
import org.springframework.transaction.annotation.Transactional;
import com.thriftbazaar.backend.dto.CartItemResponseDto;
import com.thriftbazaar.backend.dto.CartResponseDto;
import com.thriftbazaar.backend.dto.OrderItemResponseDto;
import com.thriftbazaar.backend.dto.OrderResponseDto;
import com.thriftbazaar.backend.dto.UpdateOrderStatusRequestDto;
import com.thriftbazaar.backend.entity.*;
import com.thriftbazaar.backend.repository.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public OrderController(
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            UserRepository userRepository
    ) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    // =========================
    // 1️⃣ CHECKOUT (CUSTOMER)
    // =========================
    @Transactional
    @PostMapping("/checkout")
    public void checkout() {
        System.out.println(
    "AUTH AT CHECKOUT = " +
    SecurityContextHolder.getContext().getAuthentication()
);


        // 🔐 Auth user
        String email = (String) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        List<CartItem> cartItems = cart.getItems();

        if (cartItems.isEmpty()) {
            throw new RuntimeException("Cart is empty");
        }

        // 🧾 Create Order
        Order order = new Order();
        order.setUser(user);
        order.setCreatedAt(LocalDateTime.now());
        order.setStatus(OrderStatus.PLACED);

        Order savedOrder = orderRepository.save(order);

        // 📦 Convert cart items → order items
        for (CartItem cartItem : cartItems) {

            Product product = cartItem.getProduct();

            if (product.getStock() < cartItem.getQuantity()) {
                throw new RuntimeException(
                        "Insufficient stock for product: " + product.getName()
                );
            }

            // 🔻 Reduce stock
            product.setStock(product.getStock() - cartItem.getQuantity());
            productRepository.save(product);

            // 🧾 OrderItem
            OrderItem orderItem = new OrderItem();
            orderItem.setOrder(savedOrder);
            orderItem.setProduct(product);
            orderItem.setQuantity(cartItem.getQuantity());
            orderItem.setPriceAtPurchase(product.getPrice());

            orderItemRepository.save(orderItem);
        }

        // 🧹 Clear cart
        cartItemRepository.deleteByCart(cart);
    }

    // =========================
    // 2️⃣ MY ORDERS (CUSTOMER)
    // =========================

    @GetMapping
public List<OrderResponseDto> getMyOrders() {

    String email = (String) SecurityContextHolder
            .getContext()
            .getAuthentication()
            .getPrincipal();

    User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new RuntimeException("User not found"));

    List<Order> orders = orderRepository.findByUser(user);

    return orders.stream().map(order -> {

        var items = order.getItems().stream()
                .map(i -> new OrderItemResponseDto(
                        i.getProduct().getId(),
                        i.getProduct().getName(),
                        i.getQuantity(),
                        i.getPriceAtPurchase()
                ))
                .toList();

        double total = items.stream()
                .mapToDouble(i -> i.getPriceAtPurchase() * i.getQuantity())
                .sum();

        return new OrderResponseDto(
                order.getId(),
                order.getCreatedAt(),
                order.getStatus(),
                total,
                items
        );

    }).toList();
}
@PutMapping("/{orderId}/status")
public void updateOrderStatus(
        @PathVariable Long orderId,
        @RequestBody UpdateOrderStatusRequestDto dto
) {

    Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("Order not found"));

    OrderStatus current = order.getStatus();
    OrderStatus next = dto.getStatus();

    if (next == null) {
        throw new RuntimeException("Status is required");
    }

    // 🔒 VALIDATION RULES
    switch (current) {
        case PLACED -> {
            if (next != OrderStatus.SHIPPED && next != OrderStatus.CANCELLED) {
                throw new RuntimeException(
                        "PLACED order can only be SHIPPED or CANCELLED"
                );
            }
        }
        case SHIPPED -> {
            if (next != OrderStatus.DELIVERED) {
                throw new RuntimeException(
                        "SHIPPED order can only be DELIVERED"
                );
            }
        }
        case DELIVERED, CANCELLED -> {
            throw new RuntimeException(
                    "Finalized orders cannot be updated"
            );
        }
    }

    order.setStatus(next);
    orderRepository.save(order);
}

}
