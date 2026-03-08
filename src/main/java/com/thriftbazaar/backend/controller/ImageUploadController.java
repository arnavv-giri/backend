package com.thriftbazaar.backend.controller;

import com.thriftbazaar.backend.service.ImageUploadService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Handles image uploads to Cloudinary.
 *
 * Responsibilities:
 *  - Receive multipart file from the request.
 *  - Forward to ImageUploadService.
 *  - Return the public URL.
 *
 * No validation logic. No Cloudinary SDK calls. No IO operations.
 */
@RestController
@RequestMapping("/upload")
public class ImageUploadController {

    private final ImageUploadService imageUploadService;

    public ImageUploadController(ImageUploadService imageUploadService) {
        this.imageUploadService = imageUploadService;
    }

    // POST /upload — Vendor: upload product image
    @PostMapping(consumes = "multipart/form-data", produces = "application/json")
    public ResponseEntity<Map<String, String>> uploadImage(
            @RequestParam("file") MultipartFile file
    ) {
        String url = imageUploadService.upload(file);
        return ResponseEntity.ok(Map.of("url", url));
    }
}
