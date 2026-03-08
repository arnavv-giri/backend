package com.thriftbazaar.backend.controller;

import com.thriftbazaar.backend.dto.AddToCartRequestDto;
import com.thriftbazaar.backend.dto.CartResponseDto;
import com.thriftbazaar.backend.service.CartService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Handles shopping cart operations.
 *
 * Responsibilities:
 *  - Extract authenticated email from Spring Security context.
 *  - Forward all calls to CartService.
 *  - Return ResponseEntity with appropriate HTTP status.
 *
 * No business logic. No repository access. No ownership checks.
 */
@RestController
@RequestMapping("/cart")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    // POST /cart/items — Add item to cart
    @PostMapping("/items")
    public ResponseEntity<Void> addToCart(
            @RequestBody AddToCartRequestDto dto,
            Authentication authentication
    ) {
        cartService.addToCart(authentication.getName(), dto);
        return ResponseEntity.ok().build();
    }

    // GET /cart — Get full cart with totals
    @GetMapping
    public ResponseEntity<CartResponseDto> getMyCart(Authentication authentication) {
        return ResponseEntity.ok(cartService.getCart(authentication.getName()));
    }

    // PUT /cart/items/{itemId} — Update quantity of a cart item
    @PutMapping("/items/{itemId}")
    public ResponseEntity<Void> updateCartItem(
            @PathVariable Long itemId,
            @RequestBody AddToCartRequestDto dto,
            Authentication authentication
    ) {
        cartService.updateCartItem(authentication.getName(), itemId, dto.getQuantity());
        return ResponseEntity.ok().build();
    }

    // DELETE /cart/items/{itemId} — Remove item from cart
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> deleteCartItem(
            @PathVariable Long itemId,
            Authentication authentication
    ) {
        cartService.removeCartItem(authentication.getName(), itemId);
        return ResponseEntity.noContent().build();
    }
}
