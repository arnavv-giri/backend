package com.thriftbazaar.backend.controller;

import com.thriftbazaar.backend.dto.VendorResponseDto;
import com.thriftbazaar.backend.entity.Vendor;
import com.thriftbazaar.backend.service.VendorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Handles vendor profile operations.
 *
 * Responsibilities:
 *  - Extract authenticated email from the security context.
 *  - Forward to VendorService.
 *  - Return ResponseEntity.
 *
 * No business logic. No repository access. Role guards are in SecurityConfig + VendorService.
 */
@RestController
@RequestMapping("/vendors")
public class VendorController {

    private final VendorService vendorService;

    public VendorController(VendorService vendorService) {
        this.vendorService = vendorService;
    }

    // POST /vendors — Any authenticated user: submit a vendor request
    @PostMapping
    public ResponseEntity<VendorResponseDto> registerVendor(
            @RequestBody Vendor vendorRequest,
            Authentication authentication
    ) {
        VendorResponseDto created = vendorService.createVendorProfile(
                authentication.getName(),
                vendorRequest.getStoreName()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // GET /vendors/me — Any authenticated user: get own vendor profile (pending or approved)
    @GetMapping("/me")
    public ResponseEntity<VendorResponseDto> getMyProfile(Authentication authentication) {
        return ResponseEntity.ok(
                vendorService.getCurrentVendor(authentication.getName())
        );
    }

    // GET /vendors/pending — Admin: list all unapproved vendor requests
    @GetMapping("/pending")
    public ResponseEntity<List<VendorResponseDto>> getPendingVendors() {
        return ResponseEntity.ok(vendorService.getPendingVendors());
    }

    // GET /vendors/all — Admin: list all vendors
    @GetMapping("/all")
    public ResponseEntity<List<VendorResponseDto>> getAllVendors() {
        return ResponseEntity.ok(vendorService.getAllVendors());
    }

    // PUT /vendors/{vendorId}/approve — Admin: approve a vendor (upgrades role to VENDOR)
    @PutMapping("/{vendorId}/approve")
    public ResponseEntity<VendorResponseDto> approveVendor(@PathVariable Long vendorId) {
        return ResponseEntity.ok(vendorService.approveVendor(vendorId));
    }
}
