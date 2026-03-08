package com.thriftbazaar.backend.repository;

import com.thriftbazaar.backend.entity.Product;
import com.thriftbazaar.backend.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    @Transactional
    @Modifying
    void deleteByProduct(Product product);
}
