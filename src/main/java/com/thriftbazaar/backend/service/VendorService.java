package com.thriftbazaar.backend.service;

import com.thriftbazaar.backend.dto.UserResponseDto;
import com.thriftbazaar.backend.dto.VendorResponseDto;
import com.thriftbazaar.backend.entity.User;
import com.thriftbazaar.backend.entity.Vendor;
import com.thriftbazaar.backend.exception.DuplicateResourceException;
import com.thriftbazaar.backend.exception.InvalidRequestException;
import com.thriftbazaar.backend.exception.ResourceNotFoundException;
import com.thriftbazaar.backend.exception.UnauthorizedActionException;
import com.thriftbazaar.backend.repository.UserRepository;
import com.thriftbazaar.backend.repository.VendorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class VendorService {

    private static final Logger log = LoggerFactory.getLogger(VendorService.class);

    private final VendorRepository vendorRepository;
    private final UserRepository   userRepository;
    private final UserService      userService;

    public VendorService(VendorRepository vendorRepository,
                         UserRepository userRepository,
                         UserService userService) {
        this.vendorRepository = vendorRepository;
        this.userRepository   = userRepository;
        this.userService      = userService;
    }

    // ─────────────────────────────────────────────────────────────────────
    // CREATE VENDOR PROFILE
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Any authenticated user can submit a vendor request.
     * New profiles are always unapproved until an admin acts.
     */
    @Transactional
    public VendorResponseDto createVendorProfile(String authenticatedEmail, String storeName) {

        User user = userService.getByEmail(authenticatedEmail);

        if (vendorRepository.findByUser(user).isPresent()) {
            throw new DuplicateResourceException(
                    "You have already submitted a vendor request");
        }
        if (storeName == null || storeName.isBlank()) {
            throw new InvalidRequestException("Store name is required");
        }

        Vendor vendor = new Vendor();
        vendor.setUser(user);
        vendor.setStoreName(storeName.trim());
        vendor.setApproved(false);

        Vendor saved = vendorRepository.save(vendor);
        log.info("Vendor profile created — vendorId={} userId={} storeName={}",
                saved.getId(), user.getId(), saved.getStoreName());
        return toDto(saved);
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET CURRENT VENDOR PROFILE
    // ─────────────────────────────────────────────────────────────────────

    public VendorResponseDto getCurrentVendor(String authenticatedEmail) {

        User user = userService.getByEmail(authenticatedEmail);

        Vendor vendor = vendorRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No vendor profile found for this account"));

        return toDto(vendor);
    }

    // ─────────────────────────────────────────────────────────────────────
    // APPROVE VENDOR (ADMIN ONLY)
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Approves a vendor and upgrades the linked user's role to VENDOR.
     * Both changes are committed atomically.
     */
    @Transactional
    public VendorResponseDto approveVendor(Long vendorId) {

        Vendor vendor = vendorRepository.findById(vendorId)
                .orElseThrow(() -> new ResourceNotFoundException("Vendor", vendorId));

        vendor.setApproved(true);

        User user = vendor.getUser();
        user.setRole("VENDOR");
        userRepository.save(user);

        Vendor saved = vendorRepository.save(vendor);
        log.info("Vendor approved — vendorId={} userId={} storeName={}",
                saved.getId(), user.getId(), saved.getStoreName());
        return toDto(saved);
    }

    // ─────────────────────────────────────────────────────────────────────
    // INTERNAL HELPERS
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Loads the approved vendor for the authenticated user.
     * Used by ProductService for all product write operations.
     */
    public Vendor getApprovedVendorByEmail(String email) {

        User user = userService.getByEmail(email);

        Vendor vendor = vendorRepository.findByUser(user)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "No vendor profile found for this account"));

        if (!vendor.isApproved()) {
            throw new UnauthorizedActionException(
                    "Your vendor account is pending admin approval");
        }

        return vendor;
    }

    // ─────────────────────────────────────────────────────────────────────
    // ADMIN QUERIES
    // ─────────────────────────────────────────────────────────────────────

    public List<VendorResponseDto> getPendingVendors() {
        return vendorRepository.findAllByApproved(false)
                .stream()
                .map(this::toDto)
                .toList();
    }

    public List<VendorResponseDto> getAllVendors() {
        return vendorRepository.findAll()
                .stream()
                .map(this::toDto)
                .toList();
    }

    public VendorResponseDto toDto(Vendor vendor) {
        User user = vendor.getUser();
        UserResponseDto userDto = new UserResponseDto(
                user.getId(), user.getEmail(), user.getRole(), user.getName());
        return new VendorResponseDto(
                vendor.getId(),
                vendor.getStoreName(),
                vendor.isApproved(),
                userDto);
    }
}
