package com.thriftbazaar.backend.service;

import com.thriftbazaar.backend.dto.AddToCartRequestDto;
import com.thriftbazaar.backend.dto.CartItemResponseDto;
import com.thriftbazaar.backend.dto.CartResponseDto;
import com.thriftbazaar.backend.entity.Cart;
import com.thriftbazaar.backend.entity.CartItem;
import com.thriftbazaar.backend.entity.Product;
import com.thriftbazaar.backend.entity.User;
import com.thriftbazaar.backend.exception.InvalidRequestException;
import com.thriftbazaar.backend.exception.ResourceNotFoundException;
import com.thriftbazaar.backend.exception.UnauthorizedActionException;
import com.thriftbazaar.backend.repository.CartItemRepository;
import com.thriftbazaar.backend.repository.CartRepository;
import com.thriftbazaar.backend.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CartService {

    private final CartRepository     cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository  productRepository;
    private final UserService        userService;

    public CartService(CartRepository cartRepository,
                       CartItemRepository cartItemRepository,
                       ProductRepository productRepository,
                       UserService userService) {
        this.cartRepository     = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.productRepository  = productRepository;
        this.userService        = userService;
    }

    // ─────────────────────────────────────────────────────────────────────
    // ADD ITEM
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Adds a product to the cart, or increments its quantity if it already exists.
     *
     * Business rules:
     *  - Quantity must be >= 1.
     *  - Requested quantity must not exceed available stock.
     *  - If the item is already in the cart, combined quantity must not exceed stock.
     *  - Cart is created automatically if this is the user's first item.
     *
     * @param authenticatedEmail JWT principal email
     * @param dto                product ID + quantity
     * @throws InvalidRequestException   if quantity < 1 or stock is insufficient
     * @throws ResourceNotFoundException if product does not exist
     */
    @Transactional
    public void addToCart(String authenticatedEmail, AddToCartRequestDto dto) {

        if (dto.getQuantity() < 1) {
            throw new InvalidRequestException("Quantity must be at least 1");
        }

        User user = userService.getByEmail(authenticatedEmail);

        // Get or create cart for this user
        Cart cart = cartRepository.findByUser(user)
                .orElseGet(() -> {
                    Cart c = new Cart();
                    c.setUser(user);
                    return cartRepository.save(c);
                });

        Product product = productRepository.findById(dto.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product", dto.getProductId()));

        if (product.getStock() < dto.getQuantity()) {
            throw new InvalidRequestException(
                    "Not enough stock. Available: " + product.getStock());
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
            int combined = item.getQuantity() + dto.getQuantity();
            if (combined > product.getStock()) {
                throw new InvalidRequestException(
                        "Combined quantity (" + combined + ") exceeds available stock ("
                                + product.getStock() + ")");
            }
            item.setQuantity(combined);
        }

        cartItemRepository.save(item);
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET CART
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns the full cart for the authenticated user, including line totals.
     *
     * @param authenticatedEmail JWT principal email
     * @return CartResponseDto with items and total amount
     * @throws ResourceNotFoundException if the user has no cart yet
     */
    public CartResponseDto getCart(String authenticatedEmail) {

        User user = userService.getByEmail(authenticatedEmail);

        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Cart not found for this user"));

        List<CartItemResponseDto> items = cart.getItems().stream()
                .map(item -> new CartItemResponseDto(
                        item.getId(),
                        item.getProduct().getId(),
                        item.getProduct().getName(),
                        item.getProduct().getPrice(),
                        item.getQuantity()))
                .toList();

        double total = items.stream()
                .mapToDouble(i -> i.getPrice() * i.getQuantity())
                .sum();

        return new CartResponseDto(items, total);
    }

    // ─────────────────────────────────────────────────────────────────────
    // UPDATE ITEM QUANTITY
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Replaces the quantity of a specific cart item.
     *
     * Business rules:
     *  - Quantity must be >= 1.
     *  - Item must belong to the caller's cart.
     *
     * @param authenticatedEmail JWT principal email
     * @param itemId             cart item ID
     * @param newQuantity        desired quantity
     * @throws InvalidRequestException     if quantity < 1
     * @throws ResourceNotFoundException   if cart or item not found
     * @throws UnauthorizedActionException if the item does not belong to caller's cart
     */
    @Transactional
    public void updateCartItem(String authenticatedEmail, Long itemId, int newQuantity) {

        if (newQuantity < 1) {
            throw new InvalidRequestException("Quantity must be at least 1");
        }

        User user = userService.getByEmail(authenticatedEmail);

        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Cart not found for this user"));

        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", itemId));

        assertItemBelongsToCart(item, cart);

        item.setQuantity(newQuantity);
        cartItemRepository.save(item);
    }

    // ─────────────────────────────────────────────────────────────────────
    // REMOVE ITEM
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Removes a single item from the cart.
     *
     * @param authenticatedEmail JWT principal email
     * @param itemId             cart item ID to remove
     * @throws ResourceNotFoundException   if cart or item not found
     * @throws UnauthorizedActionException if the item does not belong to caller's cart
     */
    @Transactional
    public void removeCartItem(String authenticatedEmail, Long itemId) {

        User user = userService.getByEmail(authenticatedEmail);

        Cart cart = cartRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Cart not found for this user"));

        CartItem item = cartItemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("CartItem", itemId));

        assertItemBelongsToCart(item, cart);

        cartItemRepository.delete(item);
    }

    // ─────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────

    /** Asserts that a cart item belongs to the given cart. */
    private void assertItemBelongsToCart(CartItem item, Cart cart) {
        if (!item.getCart().getId().equals(cart.getId())) {
            throw new UnauthorizedActionException(
                    "This cart item does not belong to your cart");
        }
    }
}
