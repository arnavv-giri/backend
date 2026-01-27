package com.thriftbazaar.backend.dto;
import java.util.List;

public class ProductRequestDto {

    private String name;
    private String category;
    private String size;
    private String condition;
    private double price;
    private int stock;
    private List<String> imageUrls;


    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getSize() { return size; }
    public String getCondition() { return condition; }
    public double getPrice() { return price; }
    public int getStock() { return stock; }
    public List<String> getImageUrls() {
    return imageUrls;
}
}
