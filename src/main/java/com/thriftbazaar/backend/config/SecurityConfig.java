package com.thriftbazaar.backend.config;

import com.thriftbazaar.backend.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            .csrf(csrf -> csrf.disable())

            // ⚠️ DO NOT disable anonymous
            // .anonymous(anonymous -> anonymous.disable())

            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            .authorizeHttpRequests(auth -> auth

                // OPTIONS
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // =========================
                // ORDERS — ADMIN / VENDOR FIRST
                // =========================
                .requestMatchers(HttpMethod.PUT, "/orders/*/status")
                    .hasAnyRole("ADMIN", "VENDOR")

                // =========================
                // ORDERS — CUSTOMER
                // =========================
                .requestMatchers(HttpMethod.POST, "/orders/checkout")
                    .hasRole("CUSTOMER")

                .requestMatchers(HttpMethod.GET, "/orders/**")
                    .hasRole("CUSTOMER")

                // =========================
                // CART — CUSTOMER
                // =========================
                .requestMatchers("/cart/**")
                    .hasRole("CUSTOMER")

                // =========================
                // PUBLIC
                // =========================
                .requestMatchers(HttpMethod.POST, "/users", "/users/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/products/**").permitAll()
                .requestMatchers("/health").permitAll()

                // =========================
                // ADMIN
                // =========================
                .requestMatchers(HttpMethod.GET, "/users").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/vendors/*/approve").hasRole("ADMIN")

                // =========================
                // VENDOR
                // =========================
                .requestMatchers(HttpMethod.POST, "/vendors").hasRole("VENDOR")
                .requestMatchers(HttpMethod.POST, "/products").hasRole("VENDOR")
                .requestMatchers(HttpMethod.GET, "/products/my").hasRole("VENDOR")
                .requestMatchers(HttpMethod.PUT, "/products/*/stock").hasRole("VENDOR")

                .anyRequest().authenticated()
            )

            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }
}
