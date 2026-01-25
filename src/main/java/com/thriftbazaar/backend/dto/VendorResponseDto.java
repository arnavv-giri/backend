package com.thriftbazaar.backend.dto;

public class VendorResponseDto {

    private Long id;
    private String storeName;
    private boolean approved;
    private UserResponseDto user;

    public VendorResponseDto(Long id,
                             String storeName,
                             boolean approved,
                             UserResponseDto user) {
        this.id = id;
        this.storeName = storeName;
        this.approved = approved;
        this.user = user;
    }

    public Long getId() {
        return id;
    }

    public String getStoreName() {
        return storeName;
    }

    public boolean isApproved() {
        return approved;
    }

    public UserResponseDto getUser() {
        return user;
    }
}
