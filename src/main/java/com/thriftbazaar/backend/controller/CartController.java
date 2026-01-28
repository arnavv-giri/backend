package com.thriftbazaar.backend.controller;

import com.thriftbazaar.backend.dto.AddToCartRequestDto;
import com.thriftbazaar.backend.dto.CartItemResponseDto;
import com.thriftbazaar.backend.dto.CartResponseDto;
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

import java.util.List;

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
    // 1️⃣ ADD ITEM TO CART
    // =========================
    @PostMapping("/items")
    public void addToCart(@RequestBody AddToCartRequestDto dto) {

        String email = (String) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Cart cart = cartRepository.findByUser(user)
                .orElseGet(() -> {
                    Cart c = new Cart();
                    c.setUser(user);
                    return cartRepository.save(c);
                });

        Product product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (product.getStock() < dto.getQuantity()) {
            throw new RuntimeException("Not enough stock");
        }

        CartItem item = cartItemRepository
                .findByCartAndProduct(cart, product)
                .orElse(null);

        if (item == null) {
            item = new CartItem();
            item.setCart(cart);
            item.setProduct(product);
            item.setQuantity(dto.getQuantity());
        } else {
            int newQty = item.getQuantity() + dto.getQuantity();
            if (newQty > product.getStock()) {
                throw new RuntimeException("Stock exceeded");
            }
            item.setQuantity(newQty);
        }

        cartItemRepository.save(item);
    }

    // =========================
    // 2️⃣ GET MY CART
    // =========================
    @GetMapping
    public CartResponseDto getMyCart() {

        String email = (String) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        List<CartItemResponseDto> items = cart.getItems().stream()
                .map(item -> new CartItemResponseDto(
                        item.getId(),                      // cartItemId
                        item.getProduct().getId(),         // productId
                        item.getProduct().getName(),
                        item.getProduct().getPrice(),
                        item.getQuantity()
                ))
                .toList();

        double totalAmount = items.stream()
                .mapToDouble(i -> i.getPrice() * i.getQuantity())
                .sum();

        return new CartResponseDto(items, totalAmount);
    }

    // =========================
    // 3️⃣ UPDATE ITEM QUANTITY
    // =========================
    @PutMapping("/items/{itemId}")
    public void updateCartItem(
            @PathVariable Long itemId,
            @RequestBody AddToCartRequestDto dto
    ) {
        String email = (String) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Item not found"));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new RuntimeException("Unauthorized");
        }

        if (dto.getQuantity() <= 0) {
            throw new RuntimeException("Quantity must be positive");
        }

        item.setQuantity(dto.getQuantity());
        cartItemRepository.save(item);
    }

    // =========================
    // 4️⃣ DELETE ITEM FROM CART
    // =========================
    @DeleteMapping("/items/{itemId}")
    public void deleteCartItem(@PathVariable Long itemId) {

        String email = (String) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Cart not found"));

        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new RuntimeException("Cart item not found"));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new RuntimeException("Unauthorized delete");
        }

        cartItemRepository.delete(item);
    }
}
