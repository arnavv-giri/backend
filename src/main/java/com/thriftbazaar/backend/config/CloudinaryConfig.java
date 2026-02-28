package com.thriftbazaar.backend.config;

import com.cloudinary.Cloudinary;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class CloudinaryConfig {

    @Bean
    public Cloudinary cloudinary() {

        return new Cloudinary(
                Map.of(
                        "cloud_name", "doioxjo6e",
                        "api_key", "849977999455925",
                        "api_secret", "uidV2WE65sLMH9b3F2wJe9X6a_0"
                )
        );

    }

}