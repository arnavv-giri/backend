package com.thriftbazaar.backend.controller;

import com.thriftbazaar.backend.dto.ProductRequestDto;
import com.thriftbazaar.backend.dto.ProductResponseDto;
import com.thriftbazaar.backend.entity.Product;
import com.thriftbazaar.backend.entity.User;
import com.thriftbazaar.backend.entity.Vendor;
import com.thriftbazaar.backend.repository.ProductRepository;
import com.thriftbazaar.backend.repository.UserRepository;
import com.thriftbazaar.backend.repository.VendorRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final VendorRepository vendorRepository;
    private final UserRepository userRepository;

    public ProductController(
            ProductRepository productRepository,
            VendorRepository vendorRepository,
            UserRepository userRepository
    ) {
        this.productRepository = productRepository;
        this.vendorRepository = vendorRepository;
        this.userRepository = userRepository;
    }

    // =========================
    // 1️⃣ CREATE PRODUCT (VENDOR)
    // =========================
    @PostMapping
    public ProductResponseDto createProduct(@RequestBody ProductRequestDto dto) {

        String email = (String) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Vendor vendor = vendorRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Vendor profile not found"));

        if (!vendor.isApproved()) {
            throw new RuntimeException("Vendor not approved");
        }

        Product product = new Product();
        product.setVendor(vendor);
        product.setName(dto.getName());
        product.setCategory(dto.getCategory());
        product.setSize(dto.getSize());
        product.setCondition(dto.getCondition());
        product.setPrice(dto.getPrice());
        product.setStock(dto.getStock());

        Product saved = productRepository.save(product);

        return mapToDto(saved);
    }

    // =========================
    // 2️⃣ PUBLIC: GET ALL PRODUCTS (STOCK > 0)
    // =========================
    @GetMapping
    public List<ProductResponseDto> getAllProducts() {

        return productRepository
                .findByStockGreaterThan(0)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    // =========================
    // 3️⃣ VENDOR: GET MY PRODUCTS
    // =========================
    @GetMapping("/my")
    public List<ProductResponseDto> getMyProducts() {

        String email = (String) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Vendor vendor = vendorRepository.findByUser(user)
                .orElseThrow(() -> new RuntimeException("Vendor profile not found"));

        return productRepository
                .findByVendor(vendor)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    // =========================
    // 🔁 DTO MAPPER (PRIVATE)
    // =========================
    private ProductResponseDto mapToDto(Product product) {
        return new ProductResponseDto(
                product.getId(),
                product.getName(),
                product.getCategory(),
                product.getSize(),
                product.getCondition(),
                product.getPrice(),
                product.getStock(),
                product.getVendor().getId(),
                product.getVendor().getStoreName()
        );
    }
}
