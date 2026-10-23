package com.claims.controller;

import com.claims.dto.AuthResponse;
import com.claims.dto.LoginRequest;
import com.claims.dto.RegisterRequest;
import com.claims.dto.UserResponse;
import com.claims.entity.User;
import com.claims.repository.UserRepository;
import com.claims.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;
    private final UserRepository users;

    public AuthController(AuthService authService, UserRepository users) {
        this.authService = authService;
        this.users = users;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    public UserResponse me(@AuthenticationPrincipal UserDetails principal) {
        User user = users.findByEmailIgnoreCase(principal.getUsername()).orElseThrow();
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole());
    }
}
