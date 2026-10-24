package com.claims.controller;

import com.claims.dto.AuthResponse;
import com.claims.dto.LoginRequest;
import com.claims.dto.RegisterRequest;
import com.claims.dto.UserResponse;
import com.claims.entity.User;
import com.claims.repository.UserRepository;
import com.claims.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Register as a claimant, sign in, and read the signed-in profile. "
        + "Responses carry a JWT to send as `Authorization: Bearer <token>`.")
public class AuthController {
    private final AuthService authService;
    private final UserRepository users;

    public AuthController(AuthService authService, UserRepository users) {
        this.authService = authService;
        this.users = users;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a claimant account",
            description = "Public. Always creates a CLAIMANT (officer and manager accounts are seeded, not self-service). "
                    + "Returns a JWT so the caller is signed in straight away.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created"),
            @ApiResponse(responseCode = "400", description = "Validation failed; the `errors` map names each field"),
            @ApiResponse(responseCode = "409", description = "An account with this email already exists")
    })
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in (any role)", description = "Public. Returns a JWT and the user's role.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Signed in"),
            @ApiResponse(responseCode = "400", description = "Missing or malformed fields"),
            @ApiResponse(responseCode = "401", description = "Wrong email or password (the message does not say which)")
    })
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "The signed-in user", description = "Used by the UI on start-up to re-validate a stored session.")
    @ApiResponse(responseCode = "401", description = "Missing, invalid or expired token")
    public UserResponse me(@AuthenticationPrincipal UserDetails principal) {
        User user = users.findByEmailIgnoreCase(principal.getUsername()).orElseThrow();
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole());
    }
}
