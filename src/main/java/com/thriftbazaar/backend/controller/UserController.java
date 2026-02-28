package com.thriftbazaar.backend.controller;

import com.thriftbazaar.backend.entity.User;
import com.thriftbazaar.backend.repository.UserRepository;
import com.thriftbazaar.backend.dto.LoginRequest;
import com.thriftbazaar.backend.security.JwtUtil;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping
    public User createUser(@RequestBody User user) {

        System.out.println("CREATE USER: " + user.getEmail());

        user.setPassword(passwordEncoder.encode(user.getPassword()));

        return userRepository.save(user);
    }

    @PostMapping("/login")
    public Map<String, String> login(@RequestBody LoginRequest request) {

        System.out.println("LOGIN ATTEMPT: " + request.getEmail());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> {
                    System.out.println("USER NOT FOUND");
                    return new RuntimeException("User not found");
                });

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            System.out.println("PASSWORD WRONG");
            throw new RuntimeException("Invalid credentials");
        }

        System.out.println("LOGIN SUCCESS → " + user.getEmail() + " ROLE: " + user.getRole());

        String token = JwtUtil.generateToken(user.getEmail(), user.getRole());

        return Map.of("token", token);
    }

    @GetMapping
    public List<User> getAllUsers() {

        System.out.println("GET ALL USERS");

        return userRepository.findAll();
    }
    @GetMapping("/debug-auth")
public Object debugAuth() {
    return SecurityContextHolder.getContext().getAuthentication();
}

}