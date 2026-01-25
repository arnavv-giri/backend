package com.thriftbazaar.backend.controller;

import com.thriftbazaar.backend.dto.UserResponseDto;
import com.thriftbazaar.backend.dto.VendorResponseDto;
import com.thriftbazaar.backend.entity.User;
import com.thriftbazaar.backend.entity.Vendor;
import com.thriftbazaar.backend.repository.UserRepository;
import com.thriftbazaar.backend.repository.VendorRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/vendors")
public class VendorController {

    private final VendorRepository vendorRepository;
    private final UserRepository userRepository;

    public VendorController(VendorRepository vendorRepository,
                            UserRepository userRepository) {
        this.vendorRepository = vendorRepository;
        this.userRepository = userRepository;
    }

    @PostMapping
    public VendorResponseDto registerVendor(@RequestBody Vendor vendorRequest) {

        String email = (String) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!"VENDOR".equals(user.getRole())) {
            throw new RuntimeException("Only VENDOR users can register as vendor");
        }

        Vendor vendor = new Vendor();
        vendor.setUser(user);
        vendor.setStoreName(vendorRequest.getStoreName());
        vendor.setApproved(false);

        Vendor saved = vendorRepository.save(vendor);
        return mapToDto(saved);
    }

    @PutMapping("/{vendorId}/approve")
    public VendorResponseDto approveVendor(@PathVariable Long vendorId) {

        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new RuntimeException("Vendor not found"));

        vendor.setApproved(true);
        Vendor saved = vendorRepository.save(vendor);

        return mapToDto(saved);
    }

    private VendorResponseDto mapToDto(Vendor vendor) {

        User user = vendor.getUser();

        UserResponseDto userDto =
                new UserResponseDto(
                        user.getId(),
                        user.getEmail(),
                        user.getRole()
                );

        return new VendorResponseDto(
                vendor.getId(),
                vendor.getStoreName(),
                vendor.isApproved(),
                userDto
        );
    }
}
