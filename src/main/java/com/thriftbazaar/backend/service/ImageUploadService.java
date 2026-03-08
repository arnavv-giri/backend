package com.thriftbazaar.backend.service;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.thriftbazaar.backend.exception.InvalidRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@Service
public class ImageUploadService {

    private static final Logger log = LoggerFactory.getLogger(ImageUploadService.class);

    /** Maximum permitted file size in bytes (10 MB — matches multipart config). */
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private final Cloudinary cloudinary;

    public ImageUploadService(Cloudinary cloudinary) {
        this.cloudinary = cloudinary;
    }

    // ─────────────────────────────────────────────────────────────────────
    // UPLOAD IMAGE
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Validates and uploads an image file to Cloudinary.
     *
     * Business rules:
     *  - File must not be null or empty.
     *  - Content-type must start with "image/".
     *  - File must not exceed MAX_FILE_SIZE (10 MB).
     *
     * Error handling
     * ──────────────
     * IOException from Cloudinary is caught here and re-thrown as a
     * RuntimeException with a generic message so that:
     *   (a) the GlobalExceptionHandler can map it to a 500 response, AND
     *   (b) internal Cloudinary error details (URLs, account info) are
     *       logged server-side at ERROR level but never sent to the client.
     *
     * @param file multipart image file from the request
     * @return the public secure_url of the uploaded image
     * @throws InvalidRequestException if validation fails (400 to client)
     * @throws RuntimeException        if the Cloudinary upload fails (500 to client)
     */
    public String upload(MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("File must not be empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new InvalidRequestException(
                    "Only image files are accepted (received: " + contentType + ")");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new InvalidRequestException(
                    "File size exceeds the 10 MB limit");
        }

        log.info("Uploading image — filename={} contentType={} size={}B",
                file.getOriginalFilename(), contentType, file.getSize());

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) cloudinary.uploader().upload(
                    file.getBytes(),
                    ObjectUtils.asMap(
                            "folder",        "thriftbazaar",
                            "resource_type", "image"
                    )
            );

            Object url = result.get("secure_url");
            if (url == null) {
                // Cloudinary responded but returned no URL — treat as a server error
                log.error("Cloudinary upload succeeded but returned no secure_url — result keys: {}",
                        result.keySet());
                throw new RuntimeException("Image upload failed: no URL returned from storage provider");
            }

            log.info("Image uploaded successfully — url={}", url);
            return url.toString();

        } catch (IOException e) {
            // Log the real cause with full detail for engineers; send a generic
            // message to the client so no internal infrastructure info leaks.
            log.error("Cloudinary upload failed — filename={}", file.getOriginalFilename(), e);
            throw new RuntimeException("Image upload failed. Please try again later.");
        }
    }
}
