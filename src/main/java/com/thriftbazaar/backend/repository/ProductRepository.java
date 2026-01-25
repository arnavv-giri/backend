package com.thriftbazaar.backend.repository;

import com.thriftbazaar.backend.entity.Product;
import com.thriftbazaar.backend.entity.Vendor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByVendor(Vendor vendor);
}
