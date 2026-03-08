package com.thriftbazaar.backend.dto;

/**
 * Safe user representation — never exposes the password hash.
 * Returned by GET /users/me, POST /users, and GET /users (admin).
 */
public class UserResponseDto {

    private Long   id;
    private String email;
    private String role;
    private String name;   // nullable — null until the user sets a display name

    public UserResponseDto(Long id, String email, String role, String name) {
        this.id    = id;
        this.email = email;
        this.role  = role;
        this.name  = name;
    }

    public Long   getId()    { return id; }
    public String getEmail() { return email; }
    public String getRole()  { return role; }
    public String getName()  { return name; }
}
