package com.thriftbazaar.backend.controller;

import com.thriftbazaar.backend.dto.AddToCartRequestDto;
import com.thriftbazaar.backend.entity.Cart;
import com.thriftbazaar.backend.entity.CartItem;
import com.thriftbazaar.backend.entity.Product;
import com.thriftbazaar.backend.entity.User;
import com.thriftbazaar.backend.repository.CartItemRepository;
import com.thriftbazaar.backend.repository.CartRepository;
import com.thriftbazaar.backend.repository.ProductRepository;
import com.thriftbazaar.backend.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/cart")
public class CartController {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public CartController(
            CartRepository cartRepository,
            CartItemRepository cartItemRepository,
            ProductRepository productRepository,
            UserRepository userRepository
    ) {
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository = productRepository;
        this.userRepository = userRepository;
    }

    // =========================
    // 1️⃣ ADD ITEM TO CART (CUSTOMER)
    // =========================
    @PostMapping("/items")
    public void addToCart(@RequestBody AddToCartRequestDto dto) {
        System.out.println(
    SecurityContextHolder.getContext().getAuthentication()
);


        // 🔐 Get logged-in user
        String email = (String) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 🛒 Get or create cart
        Cart cart = cartRepository.findByUser(user)
                .orElseGet(() -> {
                    Cart c = new Cart();
                    c.setUser(user);
                    return cartRepository.save(c);
                });

        // 📦 Get product
        Product product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (product.getStock() < dto.getQuantity()) {
            throw new RuntimeException("Not enough stock available");
        }

        // 🔁 Add or update cart item
        CartItem item = cartItemRepository
                .findByCartAndProduct(cart, product)
                .orElse(null);

        if (item == null) {
            item = new CartItem();
            item.setCart(cart);
            item.setProduct(product);
            item.setQuantity(dto.getQuantity());
        } else {
            item.setQuantity(item.getQuantity() + dto.getQuantity());
        }

        cartItemRepository.save(item);
    }
}
