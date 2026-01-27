package com.thriftbazaar.backend.controller;

import com.thriftbazaar.backend.dto.ProductRequestDto;
import com.thriftbazaar.backend.dto.ProductResponseDto;
import com.thriftbazaar.backend.dto.UpdateStockRequestDto;
import com.thriftbazaar.backend.entity.Product;
import com.thriftbazaar.backend.entity.ProductImage;
import com.thriftbazaar.backend.entity.User;
import com.thriftbazaar.backend.entity.Vendor;
import com.thriftbazaar.backend.repository.ProductImageRepository;
import com.thriftbazaar.backend.repository.ProductRepository;
import com.thriftbazaar.backend.repository.UserRepository;
import com.thriftbazaar.backend.repository.VendorRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductRepository productRepository;
    private final VendorRepository vendorRepository;
    private final UserRepository userRepository;
    private final ProductImageRepository productImageRepository;

    public ProductController(
            ProductRepository productRepository,
            VendorRepository vendorRepository,
            UserRepository userRepository,
            ProductImageRepository productImageRepository
    ) {
        this.productRepository = productRepository;
        this.vendorRepository = vendorRepository;
        this.userRepository = userRepository;
        this.productImageRepository = productImageRepository;
    }

    // =========================
    // 1️⃣ CREATE PRODUCT (VENDOR)
    // =========================
    @PostMapping
    public ProductResponseDto createProduct(@RequestBody ProductRequestDto dto) {

        Vendor vendor = getAuthenticatedVendor();

        Product product = new Product();
        product.setVendor(vendor);
        product.setName(dto.getName());
        product.setCategory(dto.getCategory());
        product.setSize(dto.getSize());
        product.setCondition(dto.getCondition());
        product.setPrice(dto.getPrice());
        product.setStock(dto.getStock());

        Product saved = productRepository.save(product);

        // ✅ SAVE IMAGES
        if (dto.getImageUrls() != null && !dto.getImageUrls().isEmpty()) {

            List<ProductImage> images = dto.getImageUrls()
                    .stream()
                    .map(url -> {
                        ProductImage img = new ProductImage();
                        img.setProduct(saved);
                        img.setImageUrl(url);
                        return img;
                    })
                    .toList();

            productImageRepository.saveAll(images);
        }

        return mapToDto(saved);
    }

    // =========================
    // 2️⃣ PUBLIC PRODUCTS (FILTER + PAGINATION)
    // =========================
    @GetMapping
    public List<ProductResponseDto> getPublicProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size
    ) {

        Page<Product> products = productRepository.findPublicProductsFiltered(
                category,
                minPrice,
                maxPrice,
                PageRequest.of(page, size)
        );

        return products.getContent()
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    // =========================
    // 3️⃣ VENDOR: MY PRODUCTS
    // =========================
    @GetMapping("/my")
    public List<ProductResponseDto> getMyProducts() {

        Vendor vendor = getAuthenticatedVendor();

        return productRepository.findByVendor(vendor)
                .stream()
                .map(this::mapToDto)
                .toList();
    }

    // =========================
    // 4️⃣ VENDOR: UPDATE STOCK
    // =========================
    @PutMapping("/{productId}/stock")
    public ProductResponseDto updateStock(
            @PathVariable Long productId,
            @RequestBody UpdateStockRequestDto dto
    ) {

        if (dto.getStock() < 0) {
            throw new RuntimeException("Stock cannot be negative");
        }

        Vendor vendor = getAuthenticatedVendor();

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        if (!product.getVendor().getId().equals(vendor.getId())) {
            throw new RuntimeException("You cannot update another vendor's product");
        }

        product.setStock(dto.getStock());
        return mapToDto(productRepository.save(product));
    }

    // =========================
    // 🔒 AUTH HELPER
    // =========================
    private Vendor getAuthenticatedVendor() {

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

        return vendor;
    }

    // =========================
    // 🔁 DTO MAPPER (WITH IMAGES)
    // =========================
    private ProductResponseDto mapToDto(Product product) {

        List<String> imageUrls = product.getImages() == null
                ? List.of()
                : product.getImages()
                    .stream()
                    .map(ProductImage::getImageUrl)
                    .toList();

        return new ProductResponseDto(
                product.getId(),
                product.getName(),
                product.getCategory(),
                product.getSize(),
                product.getCondition(),
                product.getPrice(),
                product.getStock(),
                product.getVendor().getId(),
                product.getVendor().getStoreName(),
                imageUrls
        );
    }
    // =========================
// 5️⃣ PUBLIC: GET PRODUCT BY ID
// =========================
@GetMapping("/{id}")
public ProductResponseDto getProductById(@PathVariable Long id) {

    Product product = productRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Product not found"));

    // hide out-of-stock or unapproved vendor products
    if (product.getStock() <= 0 || !product.getVendor().isApproved()) {
        throw new RuntimeException("Product not available");
    }

    return mapToDto(product);
}

}
