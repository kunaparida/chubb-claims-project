package com.claims.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Credentials; the seeded staff accounts are officer@claims.local and manager@claims.local")
public record LoginRequest(
        @Schema(example = "officer@claims.local") @NotBlank @Email String email,
        @Schema(example = "ChangeMe123!") @NotBlank String password) {}
