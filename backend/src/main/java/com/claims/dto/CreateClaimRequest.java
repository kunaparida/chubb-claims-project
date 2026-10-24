package com.claims.dto;

import com.claims.entity.ClaimType;
import com.claims.validation.NotFutureDate;
import com.claims.validation.ValidClaimAmount;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

@Schema(description = "A claimant reporting an incident")
public record CreateClaimRequest(
        @Schema(description = "Policy the claim is made under", example = "POL-2026-00042")
        @NotBlank @Size(max = 64) String policyNumber,
        @Schema(description = "Name of the person claiming", example = "Jane Claimant")
        @NotBlank @Size(max = 120) String claimantName,
        @Schema(description = "Must equal the signed-in account's email", example = "claimant@example.com")
        @NotBlank @Email @Size(max = 254) String claimantEmail,
        @Schema(description = "Date of the incident; not in the future", example = "2026-10-10")
        @NotNull @NotFutureDate LocalDate incidentDate,
        @Schema(description = "What happened, at least 20 characters", example = "Rear bumper damaged in a car park collision.")
        @NotBlank @Size(min = 20, max = 4000) String description,
        @Schema(description = "Amount claimed; greater than zero, at most two decimals. The API has no currency field",
                example = "1250.00")
        @NotNull @ValidClaimAmount BigDecimal amountClaimed,
        @Schema(description = "Kind of claim") @NotNull ClaimType claimType,
        @Schema(description = "Two-letter market code", example = "SG")
        @NotBlank @Pattern(regexp = "^[A-Za-z]{2}$", message = "must be a 2-letter market code such as SG") String market) {}
