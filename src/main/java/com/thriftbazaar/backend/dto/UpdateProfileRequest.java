package com.thriftbazaar.backend.dto;

/**
 * Request body for PUT /users/me.
 *
 * Only the fields present here are updateable.
 * Password changes are deliberately omitted — those would require a
 * separate "change-password" flow with current-password verification.
 *
 * All fields are optional: a null value means "leave unchanged".
 */
public class UpdateProfileRequest {

    /** New display name (stored as part of email prefix for now, or a future name column). */
    private String name;

    public String getName()              { return name; }
    public void   setName(String name)   { this.name = name; }
}
