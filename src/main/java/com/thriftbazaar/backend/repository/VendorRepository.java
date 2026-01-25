package com.thriftbazaar.backend.repository;

import com.thriftbazaar.backend.entity.Vendor;
import com.thriftbazaar.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface VendorRepository extends JpaRepository<Vendor, Long> {
    Optional<Vendor> findByUser(User user);
}
