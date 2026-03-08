package com.thriftbazaar.backend.service;

import com.thriftbazaar.backend.dto.LoginRequest;
import com.thriftbazaar.backend.dto.UpdateProfileRequest;
import com.thriftbazaar.backend.dto.UserResponseDto;
import com.thriftbazaar.backend.entity.User;
import com.thriftbazaar.backend.exception.DuplicateResourceException;
import com.thriftbazaar.backend.exception.InvalidRequestException;
import com.thriftbazaar.backend.exception.ResourceNotFoundException;
import com.thriftbazaar.backend.exception.UnauthorizedActionException;
import com.thriftbazaar.backend.repository.UserRepository;
import com.thriftbazaar.backend.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil         jwtUtil;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userRepository  = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil         = jwtUtil;
    }

    // ─────────────────────────────────────────────────────────────────────
    // REGISTER
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Validates input, checks for duplicate email, hashes password, persists user.
     *
     * @param user raw user entity from request body (password still plain-text)
     * @return UserResponseDto — never exposes the password field
     * @throws InvalidRequestException    if email or password is blank / too short
     * @throws DuplicateResourceException if email is already registered
     */
    public UserResponseDto register(User user) {

        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new InvalidRequestException("Email is required");
        }
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new InvalidRequestException("Password is required");
        }
        if (user.getPassword().length() < 6) {
            throw new InvalidRequestException("Password must be at least 6 characters");
        }

        // Role is ALWAYS CUSTOMER on registration — prevents privilege escalation.
        user.setRole("CUSTOMER");

        String normalisedEmail = user.getEmail().trim().toLowerCase();

        if (userRepository.findByEmail(normalisedEmail).isPresent()) {
            throw new DuplicateResourceException("Email already in use: " + normalisedEmail);
        }

        user.setEmail(normalisedEmail);
        user.setPassword(passwordEncoder.encode(user.getPassword()));

        User saved = userRepository.save(user);
        log.info("New user registered — id={} email={}", saved.getId(), saved.getEmail());
        return toDto(saved);
    }

    // ─────────────────────────────────────────────────────────────────────
    // LOGIN
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Validates credentials, issues JWT.
     *
     * @throws InvalidRequestException     if email or password is blank
     * @throws UnauthorizedActionException if credentials are incorrect
     */
    public Map<String, String> authenticate(LoginRequest request) {

        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new InvalidRequestException("Email is required");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new InvalidRequestException("Password is required");
        }

        String normalisedEmail = request.getEmail().trim().toLowerCase();

        User user = userRepository
                .findByEmail(normalisedEmail)
                .orElseThrow(() -> new UnauthorizedActionException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            // Log failed attempt without logging the attempted password
            log.warn("Failed login attempt for email={}", normalisedEmail);
            throw new UnauthorizedActionException("Invalid credentials");
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole());
        log.info("User logged in — id={} email={} role={}", user.getId(), user.getEmail(), user.getRole());

        return Map.of(
                "token", token,
                "role",  user.getRole(),
                "email", user.getEmail()
        );
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET ALL (ADMIN)
    // ─────────────────────────────────────────────────────────────────────

    /** Returns all registered users as DTOs — passwords never included. */
    public List<UserResponseDto> getAllUsers() {
        List<UserResponseDto> users = userRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        log.info("Admin fetched all users — count={}", users.size());
        return users;
    }

    // ─────────────────────────────────────────────────────────────────────
    // INTERNAL HELPERS
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Loads a User by email.  Used by other services that resolve the
     * authenticated principal to a full User entity.
     *
     * @throws ResourceNotFoundException if user does not exist
     */
    public User getByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User", email));
    }

    // ─────────────────────────────────────────────────────────────────────
    // GET MY PROFILE
    // ─────────────────────────────────────────────────────────────────────

    /** Returns the authenticated user’s own profile. */
    public UserResponseDto getProfile(String email) {
        User user = getByEmail(email);
        return toDto(user);
    }

    // ─────────────────────────────────────────────────────────────────────
    // UPDATE MY PROFILE
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Updates mutable profile fields.
     * Only fields that are non-null in the request are applied.
     */
    public UserResponseDto updateProfile(String email, UpdateProfileRequest request) {
        User user = getByEmail(email);

        if (request.getName() != null && !request.getName().isBlank()) {
            user.setName(request.getName().trim());
        }

        User saved = userRepository.save(user);
        log.info("Profile updated — userId={} email={}", saved.getId(), saved.getEmail());
        return toDto(saved);
    }

    // ─────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────

    private UserResponseDto toDto(User user) {
        return new UserResponseDto(user.getId(), user.getEmail(), user.getRole(), user.getName());
    }
}
