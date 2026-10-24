package com.claims.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Self-service sign-up; always creates a claimant")
public record RegisterRequest(
        @Schema(example = "claimant@example.com") @NotBlank @Email @Size(max = 254) String email,
        @Schema(description = "8 to 72 characters", example = "Claimant123!") @NotBlank @Size(min = 8, max = 72) String password,
        @Schema(example = "Jane Claimant") @NotBlank @Size(max = 120) String fullName) {}
