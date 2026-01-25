package com.thriftbazaar.backend.config;

import com.thriftbazaar.backend.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            // Stateless API → disable CSRF
            .csrf(csrf -> csrf.disable())

            // Authorization rules
            .authorizeHttpRequests(auth -> auth

                // ---------- PUBLIC ----------
                .requestMatchers("/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/users/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/users/register").permitAll()

                // ---------- ADMIN ----------
                .requestMatchers(
                        HttpMethod.PUT,
                        "/vendors/*/approve"
                ).hasAuthority("ROLE_ADMIN")

                // ---------- VENDOR ----------
                .requestMatchers(HttpMethod.POST, "/vendors").hasAuthority("ROLE_VENDOR")
                .requestMatchers("/products/**").hasAuthority("ROLE_VENDOR")

                // ---------- EVERYTHING ELSE ----------
                .anyRequest().authenticated()
            )

            // JWT filter
            .addFilterBefore(
                new JwtAuthenticationFilter(),
                UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }
}
