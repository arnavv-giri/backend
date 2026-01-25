package com.thriftbazaar.backend.dto;

public class ProductResponseDto {

    private Long id;
    private String name;
    private String category;
    private String size;
    private String condition;
    private double price;
    private int stock;
    private Long vendorId;
    private String storeName;

    public ProductResponseDto(
            Long id,
            String name,
            String category,
            String size,
            String condition,
            double price,
            int stock,
            Long vendorId,
            String storeName
    ) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.size = size;
        this.condition = condition;
        this.price = price;
        this.stock = stock;
        this.vendorId = vendorId;
        this.storeName = storeName;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getSize() { return size; }
    public String getCondition() { return condition; }
    public double getPrice() { return price; }
    public int getStock() { return stock; }
    public Long getVendorId() { return vendorId; }
    public String getStoreName() { return storeName; }
}
