package com.medislot.auth.dto;

import com.medislot.user.Role;

public record AuthResponse(String token, String tokenType, long expiresIn, Role role) {

    public static AuthResponse bearer(String token, long expiresIn, Role role) {
        return new AuthResponse(token, "Bearer", expiresIn, role);
    }
}
