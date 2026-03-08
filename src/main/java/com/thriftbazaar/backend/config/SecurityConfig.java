package com.thriftbazaar.backend.config;

import com.thriftbazaar.backend.security.JwtAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
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

    /**
     * Allowed CORS origin injected from the environment variable
     * CORS_ALLOWED_ORIGIN (set in application.properties via ${app.cors.allowed-origin}).
     *
     * Default: http://localhost:5173 (Vite dev server).
     * In production set CORS_ALLOWED_ORIGIN to your deployed frontend URL.
     */
    @Value("${app.cors.allowed-origin:http://localhost:5173}")
    private String allowedOrigin;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(auth -> auth

                // PUBLIC — no token required
                .requestMatchers(HttpMethod.POST, "/users").permitAll()
                .requestMatchers(HttpMethod.POST, "/users/login").permitAll()
                .requestMatchers(HttpMethod.GET, "/products").permitAll()
                .requestMatchers(HttpMethod.GET, "/products/search").permitAll()
                .requestMatchers(HttpMethod.GET, "/products/{id}").permitAll()
                .requestMatchers("/health").permitAll()
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // ADMIN ONLY
                .requestMatchers(HttpMethod.GET, "/users").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/vendors/*/approve").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/vendors/pending").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/vendors/all").hasRole("ADMIN")

                // ANY AUTHENTICATED USER (CUSTOMER or VENDOR) can request vendor status
                .requestMatchers(HttpMethod.POST, "/vendors").authenticated()
                .requestMatchers(HttpMethod.GET, "/vendors/me").authenticated()
                .requestMatchers(HttpMethod.GET, "/products/my").hasRole("VENDOR")
                .requestMatchers(HttpMethod.POST, "/products").hasRole("VENDOR")
                .requestMatchers(HttpMethod.PUT, "/products/*").hasRole("VENDOR")
                .requestMatchers(HttpMethod.DELETE, "/products/*").hasRole("VENDOR")
                .requestMatchers(HttpMethod.POST, "/upload").hasRole("VENDOR")

                // VENDOR ONLY — must be declared BEFORE the wildcard CUSTOMER rules
                // that follow, because Spring evaluates rules in declaration order and
                // "/orders/*" would otherwise match "/orders/vendor" first.
                .requestMatchers(HttpMethod.GET, "/orders/vendor").hasRole("VENDOR")

                // ADMIN or VENDOR — same reason: declare before /orders/* wildcard
                .requestMatchers(HttpMethod.PUT, "/orders/*/status").hasAnyRole("ADMIN", "VENDOR")

                // CUSTOMER ONLY
                .requestMatchers("/cart/**").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.POST, "/orders/checkout").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.POST, "/orders/*/cancel").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.GET,  "/orders").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.GET,  "/orders/*").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.POST, "/payments/create-order").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.POST, "/payments/verify").hasRole("CUSTOMER")

                // REVIEWS
                .requestMatchers(HttpMethod.GET,  "/reviews/product/*").permitAll()
                .requestMatchers(HttpMethod.POST, "/reviews/product/*").hasRole("CUSTOMER")
                .requestMatchers(HttpMethod.GET,  "/reviews/product/*/can-review").authenticated()

                // MESSAGING — any authenticated user (buyer or vendor)
                .requestMatchers("/messages/**").authenticated()

                // ANY AUTHENTICATED USER — own profile
                .requestMatchers(HttpMethod.GET, "/users/me").authenticated()
                .requestMatchers(HttpMethod.PUT, "/users/me").authenticated()

                .anyRequest().authenticated()
            )
            .addFilterBefore(
                    jwtAuthenticationFilter,
                    UsernamePasswordAuthenticationFilter.class
            );

        return http.build();
    }
}
