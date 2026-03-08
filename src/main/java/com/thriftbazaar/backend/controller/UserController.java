package com.thriftbazaar.backend.controller;

import com.thriftbazaar.backend.dto.LoginRequest;
import com.thriftbazaar.backend.dto.UpdateProfileRequest;
import com.thriftbazaar.backend.dto.UserResponseDto;
import com.thriftbazaar.backend.entity.User;
import com.thriftbazaar.backend.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Handles user registration and authentication.
 *
 * Responsibilities:
 *  - Parse and forward requests to UserService.
 *  - Return appropriate ResponseEntity wrappers.
 *
 * No business logic, no repository access, no validation beyond null checks.
 */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // POST /users — Register a new user
    @PostMapping
    public ResponseEntity<UserResponseDto> register(@RequestBody User user) {
        UserResponseDto created = userService.register(user);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // POST /users/login — Authenticate and receive JWT
    @PostMapping("/login")
    public ResponseEntity<Map<String, String>> login(@RequestBody LoginRequest request) {
        Map<String, String> response = userService.authenticate(request);
        return ResponseEntity.ok(response);
    }

    // GET /users — Admin: list all users
    @GetMapping
    public ResponseEntity<List<UserResponseDto>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    // GET /users/me — Return the authenticated user's own profile
    @GetMapping("/me")
    public ResponseEntity<UserResponseDto> getMyProfile(Authentication auth) {
        return ResponseEntity.ok(userService.getProfile(auth.getName()));
    }

    // PUT /users/me — Update the authenticated user's own profile
    @PutMapping("/me")
    public ResponseEntity<UserResponseDto> updateMyProfile(
            Authentication auth,
            @RequestBody UpdateProfileRequest request) {
        return ResponseEntity.ok(userService.updateProfile(auth.getName(), request));
    }
}
