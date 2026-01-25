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

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final VendorRepository vendorRepository;
    private final UserRepository userRepository;

    public ProductController(ProductRepository productRepository,
                             VendorRepository vendorRepository,
                             UserRepository userRepository) {
        this.productRepository = productRepository;
        this.vendorRepository = vendorRepository;
        this.userRepository = userRepository;
    }

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

        return new ProductResponseDto(
                saved.getId(),
                saved.getName(),
                saved.getCategory(),
                saved.getSize(),
                saved.getCondition(),
                saved.getPrice(),
                saved.getStock(),
                vendor.getId(),
                vendor.getStoreName()
        );
    }
}
