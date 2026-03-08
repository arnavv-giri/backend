package com.thriftbazaar.backend.config;

// CORS is fully configured in SecurityConfig via CorsConfigurationSource bean.
// This file is intentionally left as a no-op to avoid duplicate CORS configuration conflict.

import org.springframework.context.annotation.Configuration;

@Configuration
public class CorsGlobalConfig {
    // Intentionally empty — CORS managed in SecurityConfig
}
