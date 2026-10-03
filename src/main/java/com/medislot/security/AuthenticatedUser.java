package com.medislot.security;

import com.medislot.user.Role;

/** Principal placed in the SecurityContext after a valid JWT is parsed. */
public record AuthenticatedUser(Long userId, Role role) {
}
