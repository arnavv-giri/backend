package com.thriftbazaar.backend.service;

import com.thriftbazaar.backend.dto.CheckoutRequestDto;
import com.thriftbazaar.backend.dto.OrderResponseDto;
import com.thriftbazaar.backend.dto.OrderResponseDto.OrderItemResponseDto;
import com.thriftbazaar.backend.dto.VendorOrderResponseDto;
import com.thriftbazaar.backend.dto.VendorOrderResponseDto.VendorOrderItemDto;
import com.thriftbazaar.backend.entity.Vendor;
import com.thriftbazaar.backend.entity.Order;
import com.thriftbazaar.backend.entity.OrderItem;
import com.thriftbazaar.backend.entity.Product;
import com.thriftbazaar.backend.entity.User;
import com.thriftbazaar.backend.exception.InvalidRequestException;
import com.thriftbazaar.backend.exception.ResourceNotFoundException;
import com.thriftbazaar.backend.exception.UnauthorizedActionException;
import com.thriftbazaar.backend.repository.OrderRepository;
import com.thriftbazaar.backend.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private static final Set<String> VALID_STATUSES =
            Set.of("PENDING", "PROCESSING", "SHIPPED", "DELIVERED", "CANCELLED");

    private final OrderRepository   orderRepository;
    private final ProductRepository productRepository;
    private final UserService       userService;
    private final VendorService     vendorService;

    public OrderService(OrderRepository orderRepository,
                        ProductRepository productRepository,
                        UserService userService,
                        VendorService vendorService) {
        this.orderRepository   = orderRepository;
        this.productRepository = productRepository;
        this.userService       = userService;
        this.vendorService     = vendorService;
    }

    // ─────────────────────────────────────────────────────────────────────
    // CHECKOUT
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Creates an order from the customer's cart items.
     *
     * Everything runs in a single @Transactional boundary:
     *   1. Pre-load all products in one IN-clause query.
     *   2. Validate stock for every line item.
     *   3. Build the Order + OrderItem graph.
     *   4. Decrement stock and batch-save.
     *   5. Persist the order.
     */
    @Transactional
    public OrderResponseDto checkout(String authenticatedEmail,
                                     CheckoutRequestDto request) {

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new InvalidRequestException("Cannot place an order with an empty cart");
        }
        if (request.getDeliveryAddress() == null || request.getDeliveryAddress().isBlank()) {
            throw new InvalidRequestException("Delivery address is required");
        }

        User customer = userService.getByEmail(authenticatedEmail);

        Order order = new Order();
        order.setCustomer(customer);
        order.setStatus("PENDING");
        order.setCreatedAt(LocalDateTime.now());
        order.setDeliveryAddress(request.getDeliveryAddress().trim());

        double total = 0.0;

        // Pre-load all products in one query (N+1 elimination)
        List<Long> requestedIds = request.getItems().stream()
                .map(CheckoutRequestDto.CheckoutItemDto::getProductId)
                .toList();

        Map<Long, Product> productMap = productRepository.findAllById(requestedIds)
                .stream()
                .collect(Collectors.toMap(Product::getId, p -> p));

        for (CheckoutRequestDto.CheckoutItemDto lineDto : request.getItems()) {

            if (lineDto.getQuantity() <= 0) {
                throw new InvalidRequestException("Quantity must be at least 1 for each item");
            }

            Product product = productMap.get(lineDto.getProductId());
            if (product == null) {
                throw new ResourceNotFoundException("Product", lineDto.getProductId());
            }

            if (product.getStock() < lineDto.getQuantity()) {
                throw new InvalidRequestException(
                        "Insufficient stock for product: " + product.getName()
                        + " (available: " + product.getStock()
                        + ", requested: " + lineDto.getQuantity() + ")");
            }

            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setProductName(product.getName());
            item.setPriceAtPurchase(product.getPrice());
            item.setQuantity(lineDto.getQuantity());

            if (product.getImages() != null && !product.getImages().isEmpty()) {
                item.setImageUrl(product.getImages().get(0).getImageUrl());
            }

            order.addItem(item);
            product.setStock(product.getStock() - lineDto.getQuantity());
            total += product.getPrice() * lineDto.getQuantity();
        }

        // Batch-save all stock decrements
        List<Product> modifiedProducts = order.getItems().stream()
                .filter(i -> i.getProduct() != null)
                .map(OrderItem::getProduct)
                .toList();
        productRepository.saveAll(modifiedProducts);

        order.setTotalAmount(total);
        Order saved = orderRepository.save(order);

        log.info("Order placed — orderId={} customerId={} items={} total={}",
                saved.getId(), customer.getId(), saved.getItems().size(), saved.getTotalAmount());

        return toDto(saved);
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET MY ORDERS
    // ─────────────────────────────────────────────────────────────────────

    public List<OrderResponseDto> getMyOrders(String authenticatedEmail) {
        User customer = userService.getByEmail(authenticatedEmail);
        // LEFT JOIN FETCH without DISTINCT can produce duplicate Order roots
        // (one row per item). Collapse via LinkedHashSet to preserve order.
        List<Order> raw = orderRepository.findByCustomerWithItems(customer);
        List<Order> deduped = new ArrayList<>(new LinkedHashSet<>(raw));
        return deduped.stream()
                .map(this::toDto)
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET SINGLE ORDER
    // ─────────────────────────────────────────────────────────────────────

    public OrderResponseDto getOrderById(String authenticatedEmail, Long orderId) {

        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        User caller = userService.getByEmail(authenticatedEmail);

        boolean isOwner = order.getCustomer().getId().equals(caller.getId());
        boolean isAdmin = "ADMIN".equals(caller.getRole());

        if (!isOwner && !isAdmin) {
            throw new UnauthorizedActionException(
                    "You do not have permission to view this order");
        }

        return toDto(order);
    }

    // ─────────────────────────────────────────────────────────────────────
    // UPDATE STATUS
    // ─────────────────────────────────────────────────────────────────────

    @Transactional
    public OrderResponseDto updateStatus(Long orderId, String newStatus) {

        if (newStatus == null || !VALID_STATUSES.contains(newStatus.toUpperCase())) {
            throw new InvalidRequestException(
                    "Invalid status. Allowed values: " + VALID_STATUSES);
        }

        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        String previousStatus = order.getStatus();
        order.setStatus(newStatus.toUpperCase());
        Order saved = orderRepository.save(order);

        log.info("Order status updated — orderId={} {} → {}",
                orderId, previousStatus, saved.getStatus());

        return toDto(saved);
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET VENDOR ORDERS
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Returns all orders containing this vendor’s products, newest first.
     * Only items that belong to this vendor are included in each order’s item list.
     */
    public List<VendorOrderResponseDto> getVendorOrders(String authenticatedEmail) {
        Vendor vendor = vendorService.getApprovedVendorByEmail(authenticatedEmail);

        List<Order> raw = orderRepository.findOrdersContainingVendorProducts(vendor);
        List<Order> deduped = new ArrayList<>(new LinkedHashSet<>(raw));
        return deduped.stream()
                .map(order -> toVendorDto(order, vendor))
                .toList();
    }

    // ─────────────────────────────────────────────────────────────────────
    // DTO mapping  (package-private so PaymentService can reuse it)
    // ─────────────────────────────────────────────────────────────────────

    OrderResponseDto toDto(Order order) {
        List<OrderItemResponseDto> itemDtos = order.getItems().stream()
                .map(item -> new OrderItemResponseDto(
                        item.getId(),
                        item.getProduct() != null ? item.getProduct().getId() : null,
                        item.getProductName(),
                        item.getPriceAtPurchase(),
                        item.getQuantity(),
                        item.getImageUrl()
                ))
                .toList();

        return new OrderResponseDto(
                order.getId(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getRazorpayOrderId(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getDeliveryAddress(),
                itemDtos
        );
    }

    // ───────────────────────────────────────────────────────────────────
    // CUSTOMER: CANCEL OWN ORDER
    // ───────────────────────────────────────────────────────────────────

    /**
     * Allows the order owner to cancel their own PENDING or PROCESSING order.
     * Uses a dedicated endpoint so CUSTOMER role can call it without being
     * granted the general PUT /orders/{id}/status permission.
     */
    @Transactional
    public OrderResponseDto cancelOrder(String authenticatedEmail, Long orderId) {

        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        User caller = userService.getByEmail(authenticatedEmail);
        if (!order.getCustomer().getId().equals(caller.getId())) {
            throw new UnauthorizedActionException(
                    "You do not have permission to cancel this order");
        }

        if (!"PENDING".equals(order.getStatus()) && !"PROCESSING".equals(order.getStatus())) {
            throw new InvalidRequestException(
                    "Only PENDING or PROCESSING orders can be cancelled");
        }

        order.setStatus("CANCELLED");
        Order saved = orderRepository.save(order);
        log.info("Order cancelled by customer — orderId={} customerId={}",
                orderId, caller.getId());
        return toDto(saved);
    }

    /**
     * Maps an Order to the vendor-facing DTO, filtering items to only those
     * whose product belongs to the given vendor.
     */
    private VendorOrderResponseDto toVendorDto(Order order, Vendor vendor) {
        List<VendorOrderItemDto> vendorItems = order.getItems().stream()
                .filter(item ->
                    item.getProduct() != null &&
                    item.getProduct().getVendor() != null &&
                    item.getProduct().getVendor().getId().equals(vendor.getId())
                )
                .map(item -> new VendorOrderItemDto(
                        item.getId(),
                        item.getProduct().getId(),
                        item.getProductName(),
                        item.getPriceAtPurchase(),
                        item.getQuantity(),
                        item.getImageUrl()
                ))
                .toList();

        return new VendorOrderResponseDto(
                order.getId(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                order.getDeliveryAddress(),
                order.getCustomer().getEmail(),
                vendorItems
        );
    }
}
