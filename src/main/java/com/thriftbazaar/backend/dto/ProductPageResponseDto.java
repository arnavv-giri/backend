package com.thriftbazaar.backend.dto;

import java.util.List;

/**
 * Wraps a page of product results together with pagination metadata.
 *
 * Returned by GET /products so the frontend can render page controls
 * without knowing Spring's internal Page structure.
 *
 * Fields
 * ──────
 * content      – the products on the current page
 * page         – zero-based current page index
 * size         – number of items per page (as requested)
 * totalItems   – total matching products across all pages
 * totalPages   – how many pages exist at the requested size
 */
public class ProductPageResponseDto {

    private List<ProductResponseDto> content;
    private int    page;
    private int    size;
    private long   totalItems;
    private int    totalPages;

    public ProductPageResponseDto(
            List<ProductResponseDto> content,
            int  page,
            int  size,
            long totalItems,
            int  totalPages) {

        this.content    = content;
        this.page       = page;
        this.size       = size;
        this.totalItems = totalItems;
        this.totalPages = totalPages;
    }

    public List<ProductResponseDto> getContent()   { return content; }
    public int    getPage()                        { return page; }
    public int    getSize()                        { return size; }
    public long   getTotalItems()                  { return totalItems; }
    public int    getTotalPages()                  { return totalPages; }
}
