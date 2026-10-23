package com.claims.dto;

import com.claims.entity.Role;

public record UserResponse(Long id, String email, String fullName, Role role) {}
