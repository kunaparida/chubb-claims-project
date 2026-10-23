package com.claims.dto;

import com.claims.entity.Role;

public record AuthResponse(String token, String tokenType, long expiresIn, Long userId, String email, String fullName, Role role) {}
