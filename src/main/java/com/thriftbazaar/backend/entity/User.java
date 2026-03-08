package com.thriftbazaar.backend.entity;

import jakarta.persistence.*;

@Entity
@Table(
    name    = "users",
    indexes = {
        // Every authenticated request resolves the JWT email to a User row.
        // The UNIQUE constraint already creates an implicit index in PostgreSQL,
        // but declaring it explicitly here makes the intent clear and ensures
        // Hibernate generates a named index entry in the DDL.
        @Index(name = "idx_user_email", columnList = "email")
    }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role;

    /**
     * Optional display name.  Null for existing accounts until the user sets it.
     * Added after initial schema — nullable so existing rows are unaffected.
     */
    @Column
    private String name;

    // --- getters and setters ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }
    
    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }
    
    public void setRole(String role) {
        this.role = role;
    }

    public String getName()              { return name; }
    public void   setName(String name)   { this.name = name; }
}
