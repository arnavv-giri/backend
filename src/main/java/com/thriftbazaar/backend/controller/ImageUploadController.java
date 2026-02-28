package com.thriftbazaar.backend.controller;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/upload")
@CrossOrigin(origins = "http://localhost:5173")
public class ImageUploadController {

    private final Cloudinary cloudinary;

    public ImageUploadController(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    @PostMapping(
            consumes = "multipart/form-data",
            produces = "application/json"
    )
    public ResponseEntity<?> uploadImage(
            @RequestParam("file") MultipartFile file
    ) {

        try {

            if (file == null || file.isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "File is empty"));
            }

            Map<?, ?> uploadResult =
                    cloudinary.uploader().upload(
                            file.getBytes(),
                            ObjectUtils.asMap(
                                    "folder", "thriftbazaar",
                                    "resource_type", "image"
                            )
                    );

            String url =
                    uploadResult.get("secure_url").toString();

            return ResponseEntity.ok(
                    Map.of("url", url)
            );

        }
        catch (Exception e) {

            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "error",
                            "Upload failed",
                            "message",
                            e.getMessage()
                    ));

        }

    }

}