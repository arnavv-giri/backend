package com.thriftbazaar.backend.config;

import com.thriftbazaar.backend.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    // ✅ IGNORE login completely
    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring()
                .requestMatchers("/users/login")
                .requestMatchers("/users")
                .requestMatchers(HttpMethod.OPTIONS, "/**");
    }

    // ✅ CORS
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(List.of("http://localhost:5173"));
        configuration.setAllowedMethods(List.of("GET","POST","PUT","DELETE","OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

    @Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

    http
        .cors(cors -> {})   // ✅ keep this
        .csrf(csrf -> csrf.disable())

        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())

        .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        )

        .authorizeHttpRequests(auth -> auth

            // ✅ PUBLIC (LOGIN FIRST)
            .requestMatchers(HttpMethod.POST, "/users/login").permitAll()
            .requestMatchers(HttpMethod.POST, "/users").permitAll()
            .requestMatchers(HttpMethod.GET, "/products/**").permitAll()
            .requestMatchers("/health").permitAll()

            // ✅ allow preflight
            .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

            // ADMIN
            .requestMatchers(HttpMethod.GET, "/users").hasRole("ADMIN")
            .requestMatchers(HttpMethod.PUT, "/vendors/*/approve").hasRole("ADMIN")

            // VENDOR
            .requestMatchers(HttpMethod.POST, "/vendors").hasRole("VENDOR")
            .requestMatchers(HttpMethod.POST, "/products").hasRole("VENDOR")
            .requestMatchers(HttpMethod.GET, "/products/my").hasRole("VENDOR")
            .requestMatchers(HttpMethod.PUT, "/products/*/stock").hasRole("VENDOR")
            .requestMatchers(HttpMethod.POST, "/upload").hasRole("VENDOR")

            // CUSTOMER
            .requestMatchers("/cart/**").hasRole("CUSTOMER")
            .requestMatchers(HttpMethod.POST, "/orders/checkout").hasRole("CUSTOMER")
            .requestMatchers(HttpMethod.GET, "/orders/**").hasRole("CUSTOMER")

            // ADMIN / VENDOR
            .requestMatchers(HttpMethod.PUT, "/orders/*/status")
                .hasAnyRole("ADMIN", "VENDOR")

            .anyRequest().authenticated()
        )

        .addFilterBefore(
            jwtAuthenticationFilter,
            UsernamePasswordAuthenticationFilter.class
        );

    return http.build();
}
}