package de.travelmate.auth;

import de.travelmate.user.UserRole;

public record AuthResponse(String token, Long userId, String email, String displayName, UserRole role) {}
